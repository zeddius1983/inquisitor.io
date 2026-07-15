/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.inquisitor.harness.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the plugin against a generated consumer project: a tagged test asserting the
 * gate + evaluation env wiring end-to-end, and an untagged test that {@code evaluate}
 * must not select.
 */
class InquisitorHarnessPluginFunctionalTest {

    private static final String JUNIT_VERSION = System.getProperty("junitVersion");

    @TempDir
    private Path projectDir;

    @BeforeEach
    void generateConsumerProject() throws IOException {
        write("settings.gradle.kts", """
                rootProject.name = "consumer"
                """);
        write("build.gradle.kts", """
                plugins {
                    java
                    id("io.inquisitor.harness")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    testImplementation("org.junit.jupiter:junit-jupiter:%s")
                    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
                }

                tasks.test {
                    useJUnitPlatform {
                        excludeTags("inquisitor")
                    }
                }
                """.formatted(JUNIT_VERSION));
        write("src/test/java/ScenarioProbeTest.java", """
                import org.junit.jupiter.api.Tag;
                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertEquals;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                class ScenarioProbeTest {

                    @Test
                    @Tag("inquisitor")
                    void tagged() {
                        assertEquals("true", System.getenv("INQUISITOR_LLM_IT"));
                        assertEquals("true", System.getenv("INQUISITOR_EVAL"));
                        assertTrue(System.getProperty("inquisitor.report.dir")
                                .replace('\\\\', '/').endsWith("build/reports/inquisitor"));
                    }

                    @Test
                    void untagged() {
                    }
                }
                """);
    }

    @Test
    void evaluateRunsTaggedTestsWithGateAndEvaluationEnabled() throws IOException {
        var result = runner("evaluate").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":evaluate").getOutcome());
        var results = testResults("evaluate");
        assertTrue(results.contains("name=\"tagged()\"") || results.contains("name=\"tagged\""),
                "the tagged test must run under evaluate");
        assertFalse(results.contains("untagged"), "the untagged test must not run under evaluate");
    }

    @Test
    void evaluateEchoesTheReportHeadlineEvenWhenTestsFail() throws IOException {
        // Simulates the evaluation module's session listener (the real one needs a judge
        // model): the tagged test writes the report artifacts, then fails.
        write("src/test/java/ScenarioProbeTest.java", """
                import java.nio.file.Files;
                import java.nio.file.Path;
                import org.junit.jupiter.api.Tag;
                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.fail;

                class ScenarioProbeTest {

                    @Test
                    @Tag("inquisitor")
                    void tagged() throws Exception {
                        Path dir = Path.of(System.getProperty("inquisitor.report.dir"));
                        Files.createDirectories(dir);
                        Files.writeString(dir.resolve("evaluation.md"), \"""
                                # Inquisitor evaluation report

                                - Evaluation score: **93.8%** (80 steps evaluated)

                                <!-- headline-end -->

                                ## explicit
                                (bucket detail that must not be echoed)
                                \""");
                        Files.writeString(dir.resolve("evaluation.json"), "{}");
                        Files.writeString(dir.resolve("evaluation.html"), "<html></html>");
                        fail("a scenario failed - the report must still be echoed");
                    }
                }
                """);

        var result = runner("evaluate").buildAndFail();

        assertEquals(TaskOutcome.FAILED, result.task(":evaluate").getOutcome());
        assertTrue(result.getOutput().contains("Evaluation score: **93.8%**"),
                "the evaluate task itself must echo the headline, even on a red run");
        assertFalse(result.getOutput().contains("(bucket detail that must not be echoed)"),
                "only the headline (before the marker) is echoed");
        assertTrue(result.getOutput().contains("Evaluation report (html):"),
                "the echo links the html report when present");
    }

    @Test
    void evaluateSitsInHarnessGroup() {
        var result = runner("tasks", "--group", "harness").build();

        assertTrue(result.getOutput().contains("Harness tasks"));
        assertTrue(result.getOutput()
                .contains("evaluate - Runs Inquisitor scenario tests with LLM-as-judge evaluation."));
    }

    @Test
    void checkDoesNotRunEvaluate() throws IOException {
        var result = runner("check").build();

        assertNull(result.task(":evaluate"), "evaluate must not be wired into check");
        var results = testResults("test");
        assertTrue(results.contains("untagged"), "the untagged test runs under the plain test task");
        assertFalse(results.contains("name=\"tagged()\"") || results.contains("name=\"tagged\""),
                "the tagged test must not run under check");
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    private void write(String relativePath, String content) throws IOException {
        var file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String testResults(String taskName) throws IOException {
        try (var files = Files.list(projectDir.resolve("build/test-results/" + taskName))) {
            var xml = new StringBuilder();
            for (var file : files.filter(f -> f.getFileName().toString().endsWith(".xml")).toList()) {
                xml.append(Files.readString(file));
            }
            return xml.toString();
        }
    }
}
