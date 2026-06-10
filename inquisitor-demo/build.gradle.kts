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
    implementation(project(":inquisitor-demo-db-starter"))

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation(project(":inquisitor-harness-junit-starter"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
    options.release = 26
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("--enable-preview")
}
