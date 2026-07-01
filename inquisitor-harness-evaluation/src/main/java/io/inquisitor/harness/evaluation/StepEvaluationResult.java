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

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The judge model's structured answer: an {@link EvaluationCategory} and short findings
 * explaining it. Mapped from the judge's JSON response via structured output.
 *
 * @param category the classification; {@code null} if the model failed to produce one
 * @param findings one short note per issue found (the claim, and what the trace shows)
 */
public record StepEvaluationResult(@Nullable EvaluationCategory category, List<String> findings) {

    public StepEvaluationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
