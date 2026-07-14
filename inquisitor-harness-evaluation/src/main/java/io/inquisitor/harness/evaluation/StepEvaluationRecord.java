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

import io.inquisitor.harness.model.Outcome;
import org.jspecify.annotations.Nullable;

/**
 * One evaluated (or deliberately not-evaluated) step: everything the report needs to
 * show both sides — the actor's claim (verdict, reasoning, evidence), the ground truth
 * (the rendered tool trace), and the judge's ruling (category, score, feedback).
 *
 * @param scenario        the scenario name
 * @param scenarioSource  where the scenario was loaded from; carries the directory the
 *                        report groups by (the same name exists in several style buckets)
 * @param expectedOutcome how this run defines success ({@code FAIL} for fault detection)
 * @param stepIndex       1-based step number
 * @param stepCount       total steps in the scenario (fewer records than this means the
 *                        run stopped early — fail-fast)
 * @param stepTitle       the step heading
 * @param outcome         the actor's verdict outcome
 * @param reasoning       the actor's reasoning for the verdict
 * @param evidence        the tool responses the actor cited
 * @param toolCalls       the real trace, one rendered line per call
 * @param elapsedMillis   actor wall-clock time for the step
 * @param score           the judge's 0.0–1.0 score; meaningless when {@code category} is
 *                        {@link #NOT_EVALUATED}
 * @param category        the judge's classification, or {@link #NOT_EVALUATED} for a
 *                        harness-synthesized verdict the judge never saw
 * @param feedback        the judge's findings, joined
 */
public record StepEvaluationRecord(
        String scenario,
        @Nullable String scenarioSource,
        Outcome expectedOutcome,
        int stepIndex,
        int stepCount,
        String stepTitle,
        Outcome outcome,
        String reasoning,
        List<String> evidence,
        List<String> toolCalls,
        long elapsedMillis,
        double score,
        @Nullable String category,
        String feedback) {

    /**
     * The category recorded for a harness-synthesized verdict (empty/unparseable model
     * response): there is no actor claim to audit, so the judge is skipped and the step
     * is excluded from the mean score.
     */
    public static final String NOT_EVALUATED = "NOT_EVALUATED";

    public StepEvaluationRecord {
        evidence = List.copyOf(evidence);
        toolCalls = List.copyOf(toolCalls);
    }

    /** Whether the judge actually scored this step. */
    public boolean evaluated() {
        return !NOT_EVALUATED.equals(category);
    }
}
