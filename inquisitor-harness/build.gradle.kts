plugins {
    id("inquisitor.spring-conventions")
}

dependencies {
    api("org.springframework.ai:spring-ai-client-chat")
    api("org.springframework.ai:spring-ai-starter-model-openai")
    api("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.flexmark.all)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
