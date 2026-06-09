rootProject.name = "inquisitor.io"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.spring.io/milestone")
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}

include(
    "inquisitor-harness",
    "inquisitor-harness-starter",
    "inquisitor-harness-junit",
    "inquisitor-harness-junit-starter",
    "inquisitor-mock",
    "inquisitor-mock-starter",
    "inquisitor-demo-db-starter",
    "inquisitor-demo"
)
