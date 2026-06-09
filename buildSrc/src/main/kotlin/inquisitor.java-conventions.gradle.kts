plugins {
    `java-library`
}

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
