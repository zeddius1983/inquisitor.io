plugins {
    id("inquisitor.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation(project(":inquisitor-demo-db-starter"))

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    // Standalone harness (ScenarioTests) and the ergonomic JUnit layer
    // (ScenarioSuiteTest); the junit-starter brings the harness autoconfig too,
    // but both paths are kept explicit since both are under test here.
    testImplementation(project(":inquisitor-harness-starter"))
    testImplementation(project(":inquisitor-harness-junit-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
    options.release = 26
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
    // Stream the app's console output (incl. the controller tracing aspect) to
    // the terminal so harness-driven traffic is visible while scenarios run.
    testLogging {
        showStandardStreams = true
    }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("--enable-preview")
}
