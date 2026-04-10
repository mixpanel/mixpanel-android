import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.signing.SigningExtension

/**
 * Convention plugin for publishing Android libraries to Maven Central.
 *
 * Uses OSSRH Staging API - compatible with built-in maven-publish plugin.
 * Artifacts are staged and require manual publishing via the Central Portal.
 *
 * Adapted from mixpanel-android-common for multi-module support:
 * - Version resolution prefers project.version (set per-module) over VERSION_NAME property.
 *
 * Required environment variables for publishing (set as GitHub Actions secrets):
 * - MAVEN_CENTRAL_USERNAME: Maven Central Portal username (from User Token)
 * - MAVEN_CENTRAL_PASSWORD: Maven Central Portal password (from User Token)
 * - SIGNING_KEY: GPG private key (ASCII-armored)
 * - SIGNING_PASSWORD: GPG key passphrase
 *
 * Required project properties (set in gradle.properties or module build.gradle.kts extra):
 * - GROUP: Maven group ID (e.g., com.mixpanel.android)
 * - POM_ARTIFACT_ID: Maven artifact ID
 * - POM_NAME: Human-readable name
 * - POM_DESCRIPTION: Library description
 * - POM_URL: Project URL
 * - POM_SCM_URL: SCM URL
 * - POM_SCM_CONNECTION: SCM connection string
 * - POM_SCM_DEV_CONNECTION: SCM developer connection string
 * - POM_LICENCE_NAME: License name
 * - POM_LICENCE_URL: License URL
 * - POM_DEVELOPER_ID: Developer ID
 * - POM_DEVELOPER_NAME: Developer name
 * - POM_DEVELOPER_EMAIL: Developer email
 */
class MavenPublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("maven-publish")
            pluginManager.apply("signing")

            // Register JAR tasks for Maven Central requirements
            registerJarTasks()

            // Configure after project evaluation to ensure Android components are available
            afterEvaluate {
                configurePublishing()
                configureSigning()
                configureTaskDependencies()
                configurePublishTasks()
            }
        }
    }

    /**
     * Registers JAR tasks for Maven Central requirements.
     */
    private fun Project.registerJarTasks() {
        // Javadoc JAR from Dokka output
        tasks.register<Jar>("dokkaJavadocJar") {
            archiveClassifier.set("javadoc")
        }
    }

    /**
     * Configures task dependencies after all tasks are registered.
     */
    private fun Project.configureTaskDependencies() {
        // Configure Dokka dependency
        tasks.named<Jar>("dokkaJavadocJar").configure {
            val dokkaTask = tasks.findByName("dokkaJavadoc")
            if (dokkaTask != null) {
                dependsOn(dokkaTask)
                from(dokkaTask.outputs)
            }
        }

        // Ensure Dokka javadoc JAR is built before metadata generation
        listOf("Release", "Local", "Snapshot").forEach { pubName ->
            tasks.named("generateMetadataFileFor${pubName}Publication").configure {
                dependsOn("dokkaJavadocJar")
            }
        }
    }

    /**
     * Configures publish task aliases and validation.
     */
    private fun Project.configurePublishTasks() {
        // Disable cross-publication tasks
        tasks.named("publishReleasePublicationToMavenLocal").configure {
            enabled = false
        }
        tasks.named("publishReleasePublicationToSnapshotRepository").configure {
            enabled = false
        }
        tasks.named("publishLocalPublicationToOssrhStagingRepository").configure {
            enabled = false
        }
        tasks.named("publishLocalPublicationToSnapshotRepository").configure {
            enabled = false
        }
        tasks.named("publishLocalPublicationToLocalStagingRepository").configure {
            enabled = false
        }
        tasks.named("publishSnapshotPublicationToMavenLocal").configure {
            enabled = false
        }
        tasks.named("publishSnapshotPublicationToOssrhStagingRepository").configure {
            enabled = false
        }

        // Validate and create alias for release publishing
        tasks.named("publishReleasePublicationToOssrhStagingRepository").configure {
            doFirst {
                val missingEnvVars = listOf(
                    "MAVEN_CENTRAL_USERNAME",
                    "MAVEN_CENTRAL_PASSWORD",
                    "SIGNING_KEY",
                    "SIGNING_PASSWORD"
                ).filter { System.getenv(it).isNullOrBlank() }

                if (missingEnvVars.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "Cannot publish to Maven Central. Missing required environment variables: ${missingEnvVars.joinToString()}"
                    )
                }
            }
        }

        tasks.register("publishRelease") {
            group = "publishing"
            description = "Publishes release to Maven Central staging (requires manual publish in Portal)"
            dependsOn("publishReleasePublicationToOssrhStagingRepository")
        }

        // Validate and create alias for snapshot publishing
        tasks.named("publishSnapshotPublicationToSnapshotRepository").configure {
            doFirst {
                val missingEnvVars = listOf(
                    "MAVEN_CENTRAL_USERNAME",
                    "MAVEN_CENTRAL_PASSWORD"
                ).filter { System.getenv(it).isNullOrBlank() }

                if (missingEnvVars.isNotEmpty()) {
                    throw org.gradle.api.GradleException(
                        "Cannot publish snapshot. Missing required environment variables: ${missingEnvVars.joinToString()}"
                    )
                }
            }
        }

        tasks.register("getVersion") {
            group = "publishing"
            description = "Prints the resolved version"

            doLast {
                println(resolveVersion(project))
            }
        }

        tasks.register("getSnapshotVersion") {
            group = "publishing"
            description = "Prints the snapshot version"

            doLast {
                println(computeNextSnapshotVersion(resolveVersion(project)))
            }
        }

        tasks.register("publishSnapshot") {
            group = "publishing"
            description = "Publishes snapshot to Maven Central"
            dependsOn("publishSnapshotPublicationToSnapshotRepository")
        }

        // Local staging tasks for inspection before publishing
        tasks.register("stageRelease") {
            group = "publishing"
            description = "Stages release locally for inspection (build/staging)"
            dependsOn("publishReleasePublicationToLocalStagingRepository")

            doLast {
                println("Release staged to: ${layout.buildDirectory.get()}/staging")
                println("Inspect the artifacts before publishing")
            }
        }

        tasks.register("stageSnapshot") {
            group = "publishing"
            description = "Stages snapshot locally for inspection (build/staging)"
            dependsOn("publishSnapshotPublicationToLocalStagingRepository")

            doLast {
                println("Snapshot staged to: ${layout.buildDirectory.get()}/staging")
                println("Inspect the artifacts before publishing")
            }
        }
    }

    private fun Project.configurePublishing() {
        val groupId = propertyOrDefault("GROUP", "com.mixpanel.android")
        val artifactId = propertyOrDefault("POM_ARTIFACT_ID", project.name)
        val versionName = resolveVersion(this)

        extensions.configure<PublishingExtension> {
            publications {
                // Release publication for Maven Central (real sources and javadoc)
                create<MavenPublication>("release") {
                    from(components["release"])

                    this.groupId = groupId
                    this.artifactId = artifactId
                    this.version = versionName

                    // Dokka javadoc JAR
                    artifact(tasks.named("dokkaJavadocJar"))

                    configurePom(project)
                }

                // Local publication for development/debugging
                create<MavenPublication>("local") {
                    from(components["release"])

                    this.groupId = groupId
                    this.artifactId = artifactId
                    this.version = versionName

                    // Dokka javadoc JAR
                    artifact(tasks.named("dokkaJavadocJar"))

                    configurePom(project)
                }

                // Snapshot publication (next patch version + SNAPSHOT suffix)
                create<MavenPublication>("snapshot") {
                    from(components["release"])

                    this.groupId = groupId
                    this.artifactId = artifactId
                    this.version = computeNextSnapshotVersion(versionName)

                    // Dokka javadoc JAR
                    artifact(tasks.named("dokkaJavadocJar"))

                    configurePom(project)
                }
            }

            repositories {
                // Local staging for inspection before publishing
                maven {
                    name = "localStaging"
                    url = uri(layout.buildDirectory.dir("staging"))
                }

                // OSSRH Staging API - artifacts staged here appear in Central Portal
                maven {
                    name = "ossrhStaging"
                    url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")

                    credentials {
                        username = System.getenv("MAVEN_CENTRAL_USERNAME") ?: ""
                        password = System.getenv("MAVEN_CENTRAL_PASSWORD") ?: ""
                    }
                }

                // Snapshot repository
                maven {
                    name = "snapshot"
                    url = uri("https://central.sonatype.com/repository/maven-snapshots/")

                    credentials {
                        username = System.getenv("MAVEN_CENTRAL_USERNAME") ?: ""
                        password = System.getenv("MAVEN_CENTRAL_PASSWORD") ?: ""
                    }
                }
            }
        }
    }

    private fun MavenPublication.configurePom(project: Project) {
        pom {
            name.set(project.propertyOrDefault("POM_NAME", project.name))
            description.set(project.propertyOrDefault("POM_DESCRIPTION"))
            url.set(project.propertyOrDefault("POM_URL"))

            licenses {
                license {
                    name.set(project.propertyOrDefault("POM_LICENCE_NAME", "The Apache License, Version 2.0"))
                    url.set(project.propertyOrDefault("POM_LICENCE_URL", "https://www.apache.org/licenses/LICENSE-2.0.txt"))
                }
            }

            developers {
                developer {
                    id.set(project.propertyOrDefault("POM_DEVELOPER_ID", "mixpanel"))
                    name.set(project.propertyOrDefault("POM_DEVELOPER_NAME", "Mixpanel"))
                    email.set(project.propertyOrDefault("POM_DEVELOPER_EMAIL"))
                }
            }

            scm {
                url.set(project.propertyOrDefault("POM_SCM_URL"))
                connection.set(project.propertyOrDefault("POM_SCM_CONNECTION"))
                developerConnection.set(project.propertyOrDefault("POM_SCM_DEV_CONNECTION"))
            }
        }
    }

    private fun Project.configureSigning() {
        val signingKey = System.getenv("SIGNING_KEY")
        val signingPassword = System.getenv("SIGNING_PASSWORD")

        // Only configure signing if keys are available
        if (signingKey.isNullOrBlank()) {
            return
        }

        extensions.configure<SigningExtension> {
            useInMemoryPgpKeys(signingKey, signingPassword)

            // Sign the release publication
            val publishing = extensions.getByType(PublishingExtension::class.java)
            sign(publishing.publications.getByName("release"))

            // Only require signing for non-SNAPSHOT releases
            isRequired = !resolveVersion(this@configureSigning).endsWith("SNAPSHOT")
        }
    }
}

/**
 * Resolves the version for a project from the VERSION_NAME property.
 */
private fun resolveVersion(project: Project): String =
    project.propertyOrDefault("VERSION_NAME", project.version.toString())

/**
 * Extension function to get a project property as a String with an optional default.
 */
private fun Project.propertyOrDefault(name: String, default: String = ""): String =
    findProperty(name)?.toString() ?: default

/**
 * Computes the next snapshot version by incrementing the patch version.
 * E.g., "1.0.5" -> "1.0.6-SNAPSHOT"
 */
private fun computeNextSnapshotVersion(version: String): String {
    val parts = version.split(".")
    if (parts.size < 3) {
        return "$version.1-SNAPSHOT"
    }
    val major = parts[0]
    val minor = parts[1]
    val patch = parts[2].replace(Regex("[^0-9].*"), "").toIntOrNull() ?: 0
    return "$major.$minor.${patch + 1}-SNAPSHOT"
}
