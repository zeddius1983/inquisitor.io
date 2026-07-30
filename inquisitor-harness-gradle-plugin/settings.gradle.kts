// A standalone build (composed into the root build via includeBuild) so the demo —
// and eventually real consumers — resolve id("io.inquisitor.harness") as a plugin.
// Being separate, it cannot use the root's buildSrc conventions; the small overlap
// (toolchain, release, JUnit wiring) is inlined in build.gradle.kts.
rootProject.name = "inquisitor-harness-gradle-plugin"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        // Share the root build's catalog so versions stay centralized.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
