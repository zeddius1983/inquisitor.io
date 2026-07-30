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

import java.nio.file.Path;
import java.util.List;

/**
 * Renders an {@link EvaluationReport} into one output format — one or several files
 * (the HTML report is multi-page, like Gradle's own test report). Implementations are
 * discovered via {@code ServiceLoader}
 * ({@code META-INF/services/io.inquisitor.harness.evaluation.report.EvaluationReportRenderer})
 * and selected by {@link #name()} through the {@code evaluate} task's {@code --report}
 * option (default {@code html}) — so adding a format is a jar on the test classpath
 * with a service entry, selectable the same way as the built-ins: {@code html},
 * {@code markdown}, {@code json}.
 */
public interface EvaluationReportRenderer {

    /** The format name used to select this renderer, e.g. {@code --report=html,json}. */
    String name();

    /**
     * Renders the report into {@code dir} (which exists) and returns the files written,
     * entry page first.
     */
    List<Path> render(EvaluationReport report, Path dir);
}
