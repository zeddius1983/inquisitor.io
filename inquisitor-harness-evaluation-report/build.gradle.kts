plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    // The report model exposes StepEvaluationRecord / StepEvaluationRecorder.
    api(project(":inquisitor-harness-evaluation"))

    // The session listener (LauncherSessionListener via ServiceLoader) — always on a
    // consumer's test runtime classpath anyway (Gradle requires the launcher).
    implementation("org.junit.platform:junit-platform-launcher")
    // evaluation.json serialization.
    implementation("tools.jackson.core:jackson-databind")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
