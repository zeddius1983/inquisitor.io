plugins {
    id("inquisitor.spring-conventions")
}

dependencies {
    api(project(":inquisitor-harness"))
    implementation("org.springframework:spring-jdbc")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
