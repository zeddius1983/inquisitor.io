plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

description = "Spring Boot autoconfiguration for Logback Markdown console rendering."

dependencies {
    api(project(":inquisitor-logback-markdown"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
