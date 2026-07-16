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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.model.Outcome;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

class MarkdownReportRendererTest {

    private static StepEvaluationRecord.StepEvaluationRecordBuilder step(String scenario,
            @Nullable String source, Outcome expected, int stepIndex, int stepCount) {
        return StepEvaluationRecord.builder()
                .scenario(scenario).scenarioSource(source).expectedOutcome(expected)
                .stepIndex(stepIndex).stepCount(stepCount).stepTitle("step " + stepIndex)
                .outcome(Outcome.PASS).reasoning("fine").evidence(List.of())
                .toolCalls(List.of()).elapsedMillis(10)
                .score(1.0).category("GROUNDED").feedback("");
    }

    private static EvaluationReport sampleReport() {
        val source = "classpath:scenarios/explicit/deposit.md";
        return EvaluationReport.of(Instant.parse("2026-07-15T12:00:00Z"), Duration.ofMinutes(1),
                null, null, List.of(
                        // a grounded, perfectly-scored step: no finding
                        step("Deposit", source, Outcome.PASS, 1, 3).build(),
                        // an evaluated but imperfect step: a finding
                        step("Deposit", source, Outcome.PASS, 2, 3)
                                .score(0.0).category("CONTRADICTED")
                                .feedback("claimed reload; the trace shows none").build(),
                        // a never-scored step: the reason must surface as a finding too
                        step("Deposit", source, Outcome.PASS, 3, 3)
                                .category(StepEvaluationRecord.NOT_EVALUATED)
                                .feedback("The judge call failed (timeout); not evaluated.").build()));
    }

    @Test
    void findingsIncludeContradictedAndNeverScoredStepsButNotGroundedOnes() {
        val markdown = new MarkdownReportRenderer().renderMarkdown(sampleReport());

        assertThat(markdown)
                .contains("### Findings")
                // evaluated-but-imperfect step surfaces
                .contains("step 2 \"step 2\" — CONTRADICTED")
                .contains("claimed reload; the trace shows none")
                // never-scored step surfaces with its diagnostic reason (parity with HTML)
                .contains("step 3 \"step 3\" — NOT_EVALUATED")
                .contains("The judge call failed (timeout); not evaluated.");
        // the grounded, perfectly-scored step produces no finding
        assertThat(markdown).doesNotContain("step 1 \"step 1\"");
    }
}
