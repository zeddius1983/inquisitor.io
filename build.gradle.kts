plugins {
    `java-platform`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            artifactId = "inquisitor-bom"
        }
    }
}
