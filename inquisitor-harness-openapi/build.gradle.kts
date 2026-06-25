plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    // Brings the harness core (HttpTargetRegistry, HarnessDefaults) and, transitively,
    // Spring AI's advisor API and spring-web's RestClient.
    api(project(":inquisitor-harness"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
