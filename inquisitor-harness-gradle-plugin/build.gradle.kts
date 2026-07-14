plugins {
    id("inquisitor.java-conventions")
    `java-gradle-plugin`
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

    testImplementation(gradleTestKit())
}

// The functional test generates a consumer project; keep its JUnit version in
// lockstep with the catalog instead of hardcoding it in the test source.
tasks.test {
    systemProperty("junitVersion", libs.versions.junit.jupiter.get())
}
