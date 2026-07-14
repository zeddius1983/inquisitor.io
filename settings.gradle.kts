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

// The Gradle plugin is an included build, not a subproject: the demo resolves
// id("io.inquisitor.harness") from it the same way a real consumer would, and a
// plugin build can't be both a project of this build and a plugin source.
includeBuild("inquisitor-harness-gradle-plugin")

include(
    "inquisitor-harness",
    "inquisitor-harness-starter",
    "inquisitor-harness-junit",
    "inquisitor-harness-junit-starter",
    "inquisitor-harness-openapi",
    "inquisitor-harness-openapi-starter",
    "inquisitor-harness-evaluation",
    "inquisitor-harness-evaluation-starter",
    "inquisitor-mock",
    "inquisitor-mock-starter",
    "inquisitor-demo-db-starter",
    "inquisitor-demo"
)
