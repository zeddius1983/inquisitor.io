plugins {
    id("com.vanniktech.maven.publish")
}

// Publishes the module to Maven Central via the Central Portal (central.sonatype.com).
// Coordinates default to <group>:<project-name>:<version>; the BOM overrides its
// artifactId in the root build. Sources + Javadoc jars and GPG signatures are added
// automatically — signing credentials come from ~/.gradle/gradle.properties, never
// the repo. See README.md ("Maven Central") for the one-time account/key/token setup.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set(project.name)
        description.set(
            project.description
                ?: "Inquisitor — LLM-driven integration testing for Spring applications."
        )
        inceptionYear.set("2026")
        url.set("https://github.com/zeddius1983/inquisitor.io")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("zeddius1983")
                name.set("Aleks Valyugin")
                url.set("https://github.com/zeddius1983")
            }
        }
        scm {
            url.set("https://github.com/zeddius1983/inquisitor.io")
            connection.set("scm:git:https://github.com/zeddius1983/inquisitor.io.git")
            developerConnection.set("scm:git:ssh://git@github.com/zeddius1983/inquisitor.io.git")
        }
    }
}
