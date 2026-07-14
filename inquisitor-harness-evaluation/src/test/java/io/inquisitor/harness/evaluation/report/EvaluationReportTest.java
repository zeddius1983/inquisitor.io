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

class EvaluationReportTest {

    private static StepEvaluationRecord record(String scenario, @Nullable String source,
            Outcome expected, int stepIndex, int stepCount, Outcome outcome,
            double score, String category) {
        return StepEvaluationRecord.builder()
                .scenario(scenario)
                .scenarioSource(source)
                .expectedOutcome(expected)
                .stepIndex(stepIndex)
                .stepCount(stepCount)
                .stepTitle("step " + stepIndex)
                .outcome(outcome)
                .reasoning("reasoning")
                .evidence(List.of())
                .toolCalls(List.of("#0 t({}) -> ok"))
                .elapsedMillis(10)
                .score(score)
                .category(category)
                .feedback("")
                .build();
    }

    @Test
    void groupsByBucketFromTheSourceDirectory() {
        val report = EvaluationReport.of(Instant.now(), Duration.ofMinutes(1), null, null, List.of(
                record("A", "classpath:scenarios/explicit/a.md", Outcome.PASS, 1, 1, Outcome.PASS, 1.0, "GROUNDED"),
                record("A", "classpath:scenarios/cucumber/a.md", Outcome.PASS, 1, 1, Outcome.PASS, 1.0, "GROUNDED"),
                record("B", null, Outcome.PASS, 1, 1, Outcome.PASS, 1.0, "GROUNDED")));

        assertThat(report.buckets()).extracting(EvaluationReport.Bucket::name)
                .containsExactly("explicit", "cucumber", "(no source)");
    }

    @Test
    void splitsRepeatedRunsOfTheSameFileIntoInstances() {
        // The explicit bucket runs open-account twice in one JVM: the positive suite
        // (expect PASS, completes) and the fault suite (expect FAIL, stops at step 2).
        val source = "classpath:scenarios/explicit/open-account.md";
        val report = EvaluationReport.of(Instant.now(), Duration.ofMinutes(1), null, null, List.of(
                record("Open", source, Outcome.PASS, 1, 3, Outcome.PASS, 1.0, "GROUNDED"),
                record("Open", source, Outcome.PASS, 2, 3, Outcome.PASS, 1.0, "GROUNDED"),
                record("Open", source, Outcome.PASS, 3, 3, Outcome.PASS, 1.0, "GROUNDED"),
                record("Open", source, Outcome.FAIL, 1, 3, Outcome.PASS, 1.0, "GROUNDED"),
                record("Open", source, Outcome.FAIL, 2, 3, Outcome.FAIL, 1.0, "GROUNDED")));

        val bucket = report.buckets().getFirst();
        assertThat(bucket.scenarios()).hasSize(2);
        assertThat(bucket.scenarios().getFirst().matched()).isTrue();   // all PASS, complete
        assertThat(bucket.scenarios().getLast().matched()).isTrue();    // expected FAIL landed
        assertThat(report.totals().scenariosMatched()).isEqualTo(2);
    }

    @Test
    void expectationGateFlagsFailuresAndMissedDetections() {
        val report = EvaluationReport.of(Instant.now(), Duration.ofMinutes(1), null, null, List.of(
                // expected PASS but a step failed
                record("Broken", "classpath:scenarios/explicit/broken.md",
                        Outcome.PASS, 1, 2, Outcome.FAIL, 0.0, "CONTRADICTED"),
                // expected PASS but the run stopped before the last step (incomplete)
                record("Cut", "classpath:scenarios/explicit/cut.md",
                        Outcome.PASS, 1, 2, Outcome.PASS, 1.0, "GROUNDED"),
                // expected FAIL but every step passed — a missed detection
                record("Missed", "classpath:scenarios/explicit/missed.md",
                        Outcome.FAIL, 1, 1, Outcome.PASS, 1.0, "GROUNDED")));

        assertThat(report.totals().scenarios()).isEqualTo(3);
        assertThat(report.totals().scenariosMatched()).isZero();
    }

    @Test
    void meanScoreExcludesNotEvaluatedSteps() {
        val report = EvaluationReport.of(Instant.now(), Duration.ofMinutes(1), null, null, List.of(
                record("A", "classpath:scenarios/intent/a.md", Outcome.PASS, 1, 2, Outcome.PASS, 1.0, "GROUNDED"),
                record("A", "classpath:scenarios/intent/a.md", Outcome.PASS, 2, 2, Outcome.PASS, 0.5,
                        StepEvaluationRecord.NOT_EVALUATED)));

        assertThat(report.totals().stepsRecorded()).isEqualTo(2);
        assertThat(report.totals().stepsEvaluated()).isEqualTo(1);
        assertThat(report.totals().stepsNotEvaluated()).isEqualTo(1);
        assertThat(report.totals().meanScore()).isEqualTo(1.0);
        assertThat(report.totals().categories())
                .containsEntry("GROUNDED", 1)
                .containsEntry(StepEvaluationRecord.NOT_EVALUATED, 1);
    }
}
