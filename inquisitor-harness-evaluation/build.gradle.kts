plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    // Brings the harness core: the StepRunner seam, the ToolCallRecord/TraceKeys trace
    // contract, and — transitively — Spring AI's Evaluator API and OpenAI client the
    // judge model is built on.
    api(project(":inquisitor-harness"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
