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

/**
 * Renders an {@link EvaluationReport} into one output format. Implementations are
 * discovered via {@code ServiceLoader}
 * ({@code META-INF/services/io.inquisitor.harness.evaluation.report.EvaluationReportRenderer}),
 * so adding a format — XML, HTML, … — is a jar on the test classpath with a service
 * entry; this module ships {@link JsonReportRenderer} (the artifact of record) and
 * {@link MarkdownReportRenderer}.
 */
public interface EvaluationReportRenderer {

    /** The file name this renderer's output is written as, e.g. {@code evaluation.json}. */
    String fileName();

    /** Renders the complete document. */
    String render(EvaluationReport report);
}
