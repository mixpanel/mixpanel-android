import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.dokka") version "1.9.20"
    id("mixpanel.maven-publish")
}

android {
    namespace = "com.mixpanel.android.openfeature"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    // Match :analytics, :common and :session-replay -- keep the published Kotlin
    // metadata and the implicit kotlin-stdlib dependency on the 2.0 consumer
    // floor. See kotlinTarget in gradle/libs.versions.toml.
    coreLibrariesVersion = libs.versions.kotlinTarget.get()
    compilerOptions {
        val lang = KotlinVersion.fromVersion(libs.versions.kotlinLanguage.get())
        apiVersion.set(lang)
        languageVersion.set(lang)
    }
}

dependencies {
    // official release
    implementation("com.mixpanel.android:mixpanel-android:8.9.0")
    // or use below for local testing
    // implementation(project(":analytics"))

    implementation("dev.openfeature:kotlin-sdk-android:0.7.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
