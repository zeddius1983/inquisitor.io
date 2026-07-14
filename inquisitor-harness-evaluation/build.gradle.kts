plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    // Brings the harness core: the StepRunner seam, the ToolCallRecord/TraceKeys trace
    // contract, and — transitively — Spring AI's Evaluator API and OpenAI client the
    // judge model is built on.
    api(project(":inquisitor-harness"))

    // The report session listener (LauncherSessionListener via ServiceLoader) — always
    // on a consumer's test runtime classpath anyway (Gradle requires the launcher).
    implementation("org.junit.platform:junit-platform-launcher")
    // evaluation.json serialization (arrives transitively via Spring AI today; the
    // report writer uses it directly, so declare it).
    implementation("tools.jackson.core:jackson-databind")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
