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

package io.inquisitor.harness.evaluation;

import org.jspecify.annotations.Nullable;

/**
 * One step's recorded credibility result, for the report.
 *
 * @param scenario  the scenario name
 * @param stepIndex the 1-based step position
 * @param stepTitle the step heading
 * @param score     the 0.0–1.0 credibility score
 * @param category  the {@link EvaluationCategory} name, or {@code null} if unavailable
 * @param feedback  the judge's findings, joined
 */
public record StepEvaluationRecord(
        String scenario,
        int stepIndex,
        String stepTitle,
        double score,
        @Nullable String category,
        String feedback) {
}
