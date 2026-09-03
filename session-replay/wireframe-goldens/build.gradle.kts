import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

/**
 * Wireframe coordinate goldens, rendered off-device by Paparazzi (layoutlib).
 *
 * **Why this is a separate module and not `:session-replay/src/test`.** Paparazzi puts
 * layoutlib's *real* `android.jar` on the unit-test classpath, displacing the mockable one that
 * `:session-replay`'s Robolectric/mockk tests rely on via `unitTests.isReturnDefaultValues`.
 * Sharing a source set breaks both directions: those tests fail with `UnsatisfiedLinkError:
 * SystemProperties.native_get_int`, and Paparazzi's own Bridge fails to initialize when it is not
 * first in the JVM (cashapp/paparazzi#1979). Nothing is published from here.
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi") version "1.3.5"
    id("mixpanel.ktlint")
}

// Matches :session-replay so the `@RestrictTo(LIBRARY_GROUP)` seams this module drives
// (WireframeEmitter, SensitiveViewManager.deinitialize) are in-group for lint.
group = "com.mixpanel.android"

android {
    namespace = "com.mixpanel.android.sessionreplay.goldens"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    implementation(project(":session-replay"))

    testImplementation(libs.test.junit)
    testImplementation(libs.androidx.ui.android)
    testImplementation(libs.androidx.compose.foundation)
}

/**
 * Paparazzi 1.3.5's custom HTML reporter calls `TestResultsProvider.hasOutput`, a Gradle internal
 * removed in Gradle 9. It runs after the tests themselves pass, so dropping the HTML report is
 * enough; the XML results and Paparazzi's own report are unaffected.
 */
tasks.withType<Test>().configureEach {
    reports.html.required.set(false)
}
