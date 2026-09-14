import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Guards the Kotlin compatibility contract of *published* artifacts.
 *
 * Published modules clamp `languageVersion`/`apiVersion` to `kotlinLanguage` and
 * pin `kotlin-stdlib` to `kotlinTarget` (see gradle/libs.versions.toml). That is
 * easy to forget on a new module and easy to lose when the root KGP moves, and
 * neither `assemble` nor the unit tests notice: the regression only surfaces in
 * a consumer's build, after release.
 *
 * Concretely, Kotlin 2.1 bumped the `.kotlin_module` binary format from 1 to 2.
 * A pre-2.0 compiler does not warn about the newer format, it fails to parse it
 * with `InvalidProtocolBufferException`, and `-Xskip-metadata-version-check`
 * does not suppress that. mixpanel-android 8.11.0 and
 * mixpanel-android-openfeature 0.2.0 both shipped that way.
 *
 * This plugin is applied automatically by `mixpanel.maven-publish`, so every
 * published module is covered without opting in.
 */
class KotlinCompatibilityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val stdlibVersion = catalog.findVersion("kotlinTarget").get().requiredVersion

            val verify =
                tasks.register<VerifyKotlinCompatibilityTask>("verifyKotlinCompatibility") {
                    group = "verification"
                    description =
                        "Fails if the published AAR or POM would break Kotlin < ${stdlibVersion} consumers."
                    expectedStdlibVersion.set(stdlibVersion)
                    aar.set(layout.buildDirectory.file("outputs/aar/${project.name}-release.aar"))
                    pom.set(
                        layout.buildDirectory.file(
                            "publications/release/pom-default.xml"
                        )
                    )
                    dependsOn("assembleRelease", "generatePomFileForReleasePublication")
                }

            tasks.named("check") { dependsOn(verify) }
        }
    }
}

abstract class VerifyKotlinCompatibilityTask : DefaultTask() {
    @get:Input abstract val expectedStdlibVersion: Property<String>

    @get:InputFile abstract val aar: RegularFileProperty

    @get:InputFile abstract val pom: RegularFileProperty

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()
        checkModuleMetadata(aar.get().asFile, problems)
        checkPomStdlib(pom.get().asFile, expectedStdlibVersion.get(), problems)

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${project.path} would publish artifacts that break Kotlin consumers below ${expectedStdlibVersion.get()}:")
                    problems.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Published modules must clamp languageVersion/apiVersion to the catalog's")
                    appendLine("kotlinLanguage and set coreLibrariesVersion to kotlinTarget. See :common.")
                }
            )
        }
    }

    /**
     * A `.kotlin_module` entry starts with a length-prefixed version array:
     * `[3][major][minor][patch]`, big-endian. A clamped build writes 1.9.9999;
     * an unclamped Kotlin 2.1 build writes 2.1.0, which older compilers cannot
     * parse at all.
     */
    private fun checkModuleMetadata(aarFile: File, problems: MutableList<String>) {
        ZipFile(aarFile).use { outer ->
            val classesEntry = outer.getEntry("classes.jar") ?: return
            val classesJar = File.createTempFile("classes", ".jar")
            classesJar.deleteOnExit()
            outer.getInputStream(classesEntry).use { input ->
                classesJar.outputStream().use { input.copyTo(it) }
            }

            ZipFile(classesJar).use { jar ->
                val modules =
                    jar.entries().toList().filter {
                        it.name.startsWith("META-INF/") && it.name.endsWith(".kotlin_module")
                    }
                // A module with no Kotlin sources publishes no .kotlin_module.
                modules.forEach { entry ->
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }
                    val version = readVersionArray(bytes) ?: return@forEach
                    if (version.isNotEmpty() && version[0] >= 2) {
                        problems.add(
                            "${entry.name} declares metadata version " +
                                version.joinToString(".") +
                                " (post-2.1 module format; pre-2.0 compilers cannot parse it)"
                        )
                    }
                }
            }
        }
    }

    private fun readVersionArray(bytes: ByteArray): List<Int>? {
        if (bytes.size < 8) return null
        fun intAt(offset: Int) =
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

        val count = intAt(0)
        if (count !in 1..8 || bytes.size < 4 + count * 4) return null
        return (0 until count).map { intAt(4 + it * 4) }
    }

    private fun checkPomStdlib(pomFile: File, expected: String, problems: MutableList<String>) {
        val text = pomFile.readText()
        val match =
            Regex(
                """<artifactId>kotlin-stdlib</artifactId>\s*<version>([^<]+)</version>""",
            )
                .find(text) ?: return
        val actual = match.groupValues[1]
        if (actual != expected) {
            problems.add(
                "POM declares kotlin-stdlib $actual, expected $expected " +
                    "(a newer stdlib puts post-2.1 module metadata on every consumer's compile classpath)"
            )
        }
    }
}
