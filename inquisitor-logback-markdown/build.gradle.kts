plugins {
    id("inquisitor.java-conventions")
    id("inquisitor.publish-conventions")
}

description = "Flexmark-backed Markdown message rendering for Logback console appenders."

dependencies {
    compileOnlyApi(libs.logback.classic)
    api(libs.slf4j.api)
    compileOnlyApi(libs.jspecify)

    implementation(libs.flexmark.core)
    implementation(libs.flexmark.tables)
    implementation(libs.jansi)

    testImplementation(libs.logback.classic)
}
