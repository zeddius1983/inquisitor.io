plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    // Executor, parser, registries and model types the extensions drive.
    api(project(":inquisitor-harness"))

    // @TestTemplate / extension callbacks behind @Harness and @Scenario.
    api("org.junit.jupiter:junit-jupiter-api")

    // SpringExtension.getApplicationContext(...) to reach harness beans from the extensions.
    api("org.springframework:spring-test")
    api("org.springframework:spring-context")

    // Resource loading for classpath scenario files.
    implementation("org.springframework:spring-core")

    // Argument sources (@CsvSource, @MethodSource, ...) for parameterized scenarios.
    // Optional: consumers using them already have it (junit-jupiter); without it every
    // scenario has a single run.
    compileOnly("org.junit.jupiter:junit-jupiter-params")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Assertions for the @RequiresLlm gate test (version from the Spring Boot BOM).
    testImplementation("org.assertj:assertj-core")

    // Launcher discovery API for the @Scenario tag-selection test (the conventions
    // already put the launcher on the test runtime classpath; this makes it compilable).
    testImplementation("org.junit.platform:junit-platform-launcher")

    // The scenario-lifecycle tests (fixtures package): @Transactional for the rejection
    // test and Mockito for @MockitoBean. No database — @Sql runs against a recording
    // DataSource.
    testImplementation("org.springframework:spring-tx")
    testImplementation("org.mockito:mockito-core")
}

// Parameterized-scenario tests name their values after the method parameters, which needs
// the names in the class files — as Spring Boot's Gradle plugin compiles consumer code.
tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
}
