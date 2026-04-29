plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
}

gradlePlugin {
    plugins {
        register("mavenPublish") {
            id = "mixpanel.maven-publish"
            implementationClass = "MavenPublishConventionPlugin"
        }
        register("ktlint") {
            id = "mixpanel.ktlint"
            implementationClass = "KtlintConventionPlugin"
        }
    }
}
