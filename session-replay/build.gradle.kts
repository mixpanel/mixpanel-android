import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.dokka") version "1.9.20"
    id("mixpanel.maven-publish")
    id("mixpanel.ktlint")
}

group = "com.mixpanel.android"

android {
    namespace = "com.mixpanel.android.sessionreplay"
    compileSdk = 35
    packaging {
        resources {
            excludes += listOf("META-INF/NOTICE.md")
        }
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "VERSION_NAME", "\"${project.findProperty("VERSION_NAME") ?: "0.0.0"}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 34
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    lint {
        disable += listOf(
            "ModifierFactoryExtensionFunction",
            "ModifierFactoryReturnType",
            "ModifierFactoryUnreferencedReceiver"
        )
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
    jvmToolchain(17)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
    // Production dependencies
    api(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.curtains)
    implementation(libs.androidx.ui.android)
    implementation(libs.mixpanel.android.common)

    // Test dependencies
    testImplementation(libs.test.junit)
    testImplementation(libs.test.kotlinx.coroutines)
    testImplementation(libs.test.mockito.kotlin)
    testImplementation(libs.test.mockk)
    testImplementation(libs.test.mockito.core)
    testImplementation(libs.test.robolectric)

    // Android test dependencies
    androidTestImplementation(libs.test.androidx.junit)
    androidTestImplementation(libs.test.androidx.espresso.core)
    androidTestImplementation(libs.test.mockito.android)
    androidTestImplementation(libs.test.androidx.runner)
    androidTestImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.mixpanel)
    androidTestUtil(libs.test.androidx.orchestrator)
}
