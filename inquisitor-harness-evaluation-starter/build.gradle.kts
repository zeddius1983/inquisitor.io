plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    api(project(":inquisitor-harness-evaluation"))
    // The autoconfiguration decorates the harness ChatClient's tools and wraps its
    // StepRunner, so the harness autoconfiguration must be on the classpath too.
    api(project(":inquisitor-harness-starter"))
    // Report artifacts (evaluation.{json,md} at JUnit-session close). Excludable: the
    // report registration is @ConditionalOnClass-guarded, so evaluation degrades to
    // score-only when a consumer excludes this module.
    implementation(project(":inquisitor-harness-evaluation-report"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
