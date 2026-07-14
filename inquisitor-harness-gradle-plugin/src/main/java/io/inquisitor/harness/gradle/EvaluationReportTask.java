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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/**
 * Surfaces the evaluation report the test JVM wrote: echoes the headline block of
 * {@code evaluation.md} (everything before the {@code <!-- headline-end -->} marker)
 * plus the artifact paths. Wired as a finalizer of {@code evaluate} so it runs even —
 * especially — when scenario tests fail; skipped when no report was written (e.g. the
 * evaluation modules are not on the test classpath).
 */
public abstract class EvaluationReportTask extends DefaultTask {

    private static final String HEADLINE_END = "<!-- headline-end -->";

    /** Where the test JVM wrote the report; not an input — this task only echoes. */
    @Internal
    public abstract DirectoryProperty getReportDir();

    /** The markdown report location, or null-safe path used by onlyIf. */
    @Internal
    public Path getMarkdownReport() {
        return getReportDir().get().getAsFile().toPath().resolve("evaluation.md");
    }

    @TaskAction
    void printHeadline() {
        var markdown = getMarkdownReport();
        try {
            var headline = new StringBuilder();
            for (var line : Files.readAllLines(markdown)) {
                if (line.contains(HEADLINE_END)) {
                    break;
                }
                headline.append(line).append(System.lineSeparator());
            }
            getLogger().lifecycle(headline.toString().stripTrailing());
            getLogger().lifecycle("Evaluation report: {}", markdown.toAbsolutePath());
            getLogger().lifecycle("Evaluation data:   {}",
                    markdown.resolveSibling("evaluation.json").toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the evaluation report " + markdown, e);
        }
    }
}
