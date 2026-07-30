plugins {
    id("inquisitor.java-conventions")
    id("inquisitor.publish-conventions")
}

description = "Flexmark-backed Markdown message rendering for Logback console appenders."

dependencies {
    api(libs.logback.classic)
    api(libs.slf4j.api)
    compileOnlyApi(libs.jspecify)

    implementation(libs.flexmark.all)
    implementation(libs.jansi)
}
