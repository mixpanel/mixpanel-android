plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("mavenPublish") {
            id = "mixpanel.maven-publish"
            implementationClass = "MavenPublishConventionPlugin"
        }
    }
}
