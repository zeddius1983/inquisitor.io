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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

/**
 * Echoes the evaluation report's Markdown headline (everything before the
 * {@code <!-- headline-end -->} marker) plus the artifact location once the root test
 * suite finishes. {@code afterSuite} on the root descriptor runs inside the
 * {@code evaluate} task after every test, but <em>before</em> the task fails on failing
 * tests — so the echo survives a red run, no finalizer task needed. Silent when no
 * report was written (the evaluation modules weren't on the test classpath).
 */
final class EvaluationReportEcho implements TestListener {

    private static final Logger LOGGER = Logging.getLogger(EvaluationReportEcho.class);
    private static final String HEADLINE_END = "<!-- headline-end -->";

    private final Provider<Path> markdownReport;

    EvaluationReportEcho(Provider<Path> markdownReport) {
        this.markdownReport = markdownReport;
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.getParent() != null) {
            return;
        }
        var markdown = markdownReport.get();
        if (!Files.exists(markdown)) {
            return;
        }
        try {
            var headline = new StringBuilder(System.lineSeparator());
            for (var line : Files.readAllLines(markdown)) {
                if (line.contains(HEADLINE_END)) {
                    break;
                }
                headline.append(line).append(System.lineSeparator());
            }
            LOGGER.lifecycle(headline.toString().stripTrailing());
            LOGGER.lifecycle("Evaluation report: {}", markdown.toAbsolutePath());
            var html = markdown.resolveSibling("evaluation.html");
            if (Files.exists(html)) {
                LOGGER.lifecycle("Evaluation report (html): {}", html.toAbsolutePath());
            }
        } catch (IOException e) {
            // The echo is convenience output; never let it break the test task.
            LOGGER.warn("Could not read the evaluation report {}", markdown, e);
        }
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
    }
}
