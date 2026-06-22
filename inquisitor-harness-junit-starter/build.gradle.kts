plugins {
    id("inquisitor.spring-conventions")
}

dependencies {
    // A consumer's single test dependency: the JUnit layer (@InquisitorTest + base
    // class) plus the harness autoconfiguration it relies on.
    api(project(":inquisitor-harness-junit"))
    api(project(":inquisitor-harness-starter"))
}
