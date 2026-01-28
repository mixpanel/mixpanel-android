import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

configure<LibraryExtension> {
    namespace = "com.mixpanel.android.kotlin"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("proguard.txt")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = false
        targetSdk = 34
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json)
}
