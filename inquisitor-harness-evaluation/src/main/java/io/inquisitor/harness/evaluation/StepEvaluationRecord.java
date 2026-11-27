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
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One evaluated (or deliberately not-evaluated) step: everything the report needs to
 * show both sides — the actor's claim (verdict, reasoning, evidence), the ground truth
 * (the rendered tool trace), and the judge's ruling (category, score, feedback).
 *
 * @param scenario        the scenario name
 * @param scenarioSource  where the scenario was loaded from (the markdown file)
 * @param scenarioGroup   the run context — the JUnit suite class when run through the
 *                        JUnit layer, absent standalone; the report's grouping key
 *                        (falling back to the source directory)
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
 * @param rounds          actor attempts and their reviews, empty for observation-only evaluation
 * @param unresolved      execution did not establish an application verdict; outcome is a synthetic fallback
 * @param terminationReason why supervised execution was stopped, empty for accepted execution
 */
@Builder(toBuilder = true)
public record StepEvaluationRecord(
        String scenario,
        @Nullable String scenarioSource,
        @Nullable String scenarioGroup,
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
        String feedback,
        List<StepReviewRound> rounds,
        boolean unresolved,
        String terminationReason) {

    public StepEvaluationRecord(String scenario, @Nullable String scenarioSource, @Nullable String scenarioGroup,
            Outcome expectedOutcome, int stepIndex, int stepCount, String stepTitle, Outcome outcome,
            String reasoning, List<String> evidence, List<String> toolCalls, long elapsedMillis,
            double score, @Nullable String category, String feedback) {
        this(scenario, scenarioSource, scenarioGroup, expectedOutcome, stepIndex, stepCount, stepTitle,
                outcome, reasoning, evidence, toolCalls, elapsedMillis, score, category, feedback,
                List.of(), false, "");
    }

    /**
     * The category recorded for a harness-synthesized verdict (empty/unparseable model
     * response): there is no actor claim to audit, so the judge is skipped and the step
     * is excluded from the mean score.
     */
    public static final String NOT_EVALUATED = "NOT_EVALUATED";

    public StepEvaluationRecord {
        evidence = List.copyOf(evidence);
        toolCalls = List.copyOf(toolCalls);
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
        terminationReason = terminationReason == null ? "" : terminationReason;
    }

    /** Whether the judge actually scored this step. */
    public boolean evaluated() {
        return !unresolved && !NOT_EVALUATED.equals(category);
    }

    public int correctionCount() {
        return Math.max(0, rounds.size() - 1);
    }

    public boolean acceptedInitially() {
        return !rounds.isEmpty() && rounds.getFirst().review() != null
                && rounds.getFirst().review().decision() == StepReview.Decision.ACCEPT;
    }
}
