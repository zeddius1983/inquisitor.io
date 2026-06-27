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

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Assertions for the @RequiresLlm gate test (version from the Spring Boot BOM).
    testImplementation("org.assertj:assertj-core")
}
