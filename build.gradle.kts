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

dependencies {
    constraints {
        api(project(":inquisitor-harness"))
        api(project(":inquisitor-harness-starter"))
        api(project(":inquisitor-harness-junit"))
        api(project(":inquisitor-harness-junit-starter"))
    }
}
