plugins {
    `java-library`
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Import junit-bom so all org.junit.platform artifacts get consistent versions.
// Gradle 9 requires junit-platform-launcher on the test runtime classpath and it has
// no version of its own — the BOM provides it.
dependencies {
    "testImplementation"(platform("org.junit:junit-bom:${libs.junit.jupiter.get().version}"))
    "testImplementation"(libs.junit.jupiter)
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
