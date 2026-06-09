plugins { `kotlin-dsl` }

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.6")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
