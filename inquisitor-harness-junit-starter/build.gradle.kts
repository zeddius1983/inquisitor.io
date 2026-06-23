plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    // A consumer's single test dependency: the JUnit layer (@Harness / @Scenario)
    // plus the harness autoconfiguration it relies on.
    api(project(":inquisitor-harness-junit"))
    api(project(":inquisitor-harness-starter"))
}
