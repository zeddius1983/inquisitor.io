plugins {
    id("inquisitor.spring-conventions")
}

dependencies {
    // Executor, parser, registries and model types are part of the base class's surface.
    api(project(":inquisitor-harness"))

    // @TestFactory / DynamicNode / @InquisitorTest are surface a consumer extends.
    api("org.junit.jupiter:junit-jupiter-api")

    // SpringExtension wiring, @Autowired, Environment for the base class.
    api("org.springframework:spring-test")
    api("org.springframework:spring-context")

    // PathMatchingResourcePatternResolver for classpath scenario discovery.
    implementation("org.springframework:spring-core")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
