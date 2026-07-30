plugins {
    `java-gradle-plugin`
}

// Inlined from inquisitor.java-conventions (buildSrc is not visible to an
// included build): toolchain 26, release 21 so the plugin loads on Gradle
// running Java 21+, JUnit Platform with the launcher the BOM versions.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

gradlePlugin {
    plugins {
        create("inquisitorHarness") {
            id = "io.inquisitor.harness"
            implementationClass = "io.inquisitor.harness.gradle.InquisitorHarnessPlugin"
            displayName = "Inquisitor harness"
            description = "Runs Inquisitor scenario tests with LLM-as-judge evaluation."
        }
    }
}

dependencies {
    compileOnly(libs.jspecify)

    testImplementation(platform("org.junit:junit-bom:${libs.junit.jupiter.get().version}"))
    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // The functional test generates a consumer project; keep its JUnit version in
    // lockstep with the catalog instead of hardcoding it in the test source.
    systemProperty("junitVersion", libs.versions.junit.jupiter.get())
}
