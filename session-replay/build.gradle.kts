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
    api("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("com.squareup.curtains:curtains:1.2.5")
    implementation("androidx.compose.ui:ui-android:1.7.7")
    implementation("com.mixpanel.android:mixpanel-android-common:1.0.1")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.robolectric:robolectric:4.14.1")

    // Android test dependencies
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("org.mockito:mockito-android:5.14.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("com.mixpanel.android:mixpanel-android:8.6.0")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
}
