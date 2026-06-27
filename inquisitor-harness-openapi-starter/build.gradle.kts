plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    api(project(":inquisitor-harness-openapi"))
    // The advisor augments the harness ChatClient and uses its HttpTargetRegistry,
    // so the harness autoconfiguration must be on the classpath too.
    api(project(":inquisitor-harness-starter"))

    // The @OpenApiDiscovery ContextCustomizerFactory hooks into the Spring TestContext
    // framework. This starter is itself a test-scope dependency for consumers, so
    // spring-test is already on their test classpath.
    implementation("org.springframework:spring-test")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
