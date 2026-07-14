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

package io.inquisitor.harness.evaluation.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import lombok.val;

/**
 * Writes an {@link EvaluationReport} through every configured
 * {@link EvaluationReportRenderer} — one file per renderer, all into one directory.
 * {@link #discover()} assembles the renderer set via {@code ServiceLoader}, so
 * formats are pluggable: this module contributes JSON + Markdown; any jar on the test
 * classpath can contribute more.
 */
public class EvaluationReportWriter {

    private final List<EvaluationReportRenderer> renderers;

    public EvaluationReportWriter(List<EvaluationReportRenderer> renderers) {
        this.renderers = List.copyOf(renderers);
    }

    /** A writer over every {@code ServiceLoader}-discovered renderer. */
    public static EvaluationReportWriter discover() {
        return new EvaluationReportWriter(ServiceLoader.load(EvaluationReportRenderer.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList());
    }

    /** Renders and writes every format into {@code dir} (created if absent). */
    public List<Path> write(EvaluationReport report, Path dir) {
        try {
            Files.createDirectories(dir);
            val files = new ArrayList<Path>();
            for (val renderer : renderers) {
                val file = dir.resolve(renderer.fileName());
                Files.writeString(file, renderer.render(report));
                files.add(file);
            }
            return List.copyOf(files);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the evaluation report to " + dir, e);
        }
    }
}
