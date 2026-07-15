plugins {
    `java-platform`
    id("inquisitor.publish-conventions")
}

// The published BOM (`inquisitor-bom`) aligns the versions of the Inquisitor modules
// a consumer depends on. The root project is named "inquisitor.io", so override the
// artifactId via explicit coordinates; group and version come from gradle.properties.
mavenPublishing {
    coordinates(project.group.toString(), "inquisitor-bom", project.version.toString())
}

// The Gradle plugin lives in an included build, which `./gradlew build` would
// otherwise skip — hook its verification into the root lifecycle so CI keeps
// running its functional tests.
tasks.named("check") {
    dependsOn(gradle.includedBuild("inquisitor-harness-gradle-plugin").task(":check"))
}

dependencies {
    constraints {
        api(project(":inquisitor-harness"))
        api(project(":inquisitor-harness-starter"))
        api(project(":inquisitor-harness-junit"))
        api(project(":inquisitor-harness-junit-starter"))
        api(project(":inquisitor-harness-openapi"))
        api(project(":inquisitor-harness-openapi-starter"))
        api(project(":inquisitor-harness-evaluation"))
        api(project(":inquisitor-harness-evaluation-report"))
        api(project(":inquisitor-harness-evaluation-starter"))
    }
}
