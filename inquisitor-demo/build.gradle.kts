plugins {
    id("inquisitor.spring-conventions")
    id("org.springframework.boot")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
    options.release = 26
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}
