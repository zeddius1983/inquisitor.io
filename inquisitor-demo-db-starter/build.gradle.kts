plugins {
    id("inquisitor.spring-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-jdbc")
    api("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("org.testcontainers:testcontainers-postgresql")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
