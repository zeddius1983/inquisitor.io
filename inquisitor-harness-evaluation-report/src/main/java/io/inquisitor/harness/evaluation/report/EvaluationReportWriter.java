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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Writes an {@link EvaluationReport} through the selected
 * {@link EvaluationReportRenderer}s — all into one directory. {@link #discover(Set)}
 * assembles the renderer set via {@code ServiceLoader} filtered by renderer
 * {@linkplain EvaluationReportRenderer#name() name} (the {@code --report} tokens), so
 * formats are pluggable: this module contributes {@code html}, {@code markdown} and
 * {@code json}; any jar on the test classpath can contribute more.
 */
@Slf4j
public class EvaluationReportWriter {

    private final List<EvaluationReportRenderer> renderers;

    public EvaluationReportWriter(List<EvaluationReportRenderer> renderers) {
        this.renderers = List.copyOf(renderers);
    }

    /**
     * A writer over the {@code ServiceLoader}-discovered renderers whose names are in
     * {@code formats}; unknown names are logged and skipped.
     */
    public static EvaluationReportWriter discover(Set<String> formats) {
        val available = ServiceLoader.load(EvaluationReportRenderer.class).stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        val selected = available.stream()
                .filter(renderer -> formats.contains(renderer.name()))
                .toList();
        val unknown = new LinkedHashSet<>(formats);
        selected.forEach(renderer -> unknown.remove(renderer.name()));
        if (!unknown.isEmpty()) {
            log.warn("Unknown report format(s) {} — available: {}", unknown,
                    available.stream().map(EvaluationReportRenderer::name).toList());
        }
        return new EvaluationReportWriter(selected);
    }

    /** Renders every selected format into {@code dir} (created if absent). */
    public List<Path> write(EvaluationReport report, Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the report directory " + dir, e);
        }
        val files = new ArrayList<Path>();
        for (val renderer : renderers) {
            files.addAll(renderer.render(report, dir));
        }
        return List.copyOf(files);
    }
}
