rootProject.name = "buildSrc"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.spring.io/milestone")
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
