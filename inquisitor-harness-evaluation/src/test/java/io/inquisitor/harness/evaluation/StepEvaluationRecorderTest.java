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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import io.inquisitor.harness.model.ToolCallRecord;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.evaluation.EvaluationResponse;

class StepEvaluationRecorderTest {

    private static final Scenario SCENARIO = new Scenario("Transfer", "desc",
            List.of(new Step(1, "Open", "open it"), new Step(2, "Check", "check it")),
            "classpath:scenarios/explicit/transfer.md");

    private static StepRequest request(int stepIndex) {
        val scenario = SCENARIO.withGroup("TransferSuiteTest");
        return StepRequest.of("conv", scenario, scenario.steps().get(stepIndex - 1));
    }

    private static StepRun run(StepVerdict verdict) {
        return new StepRun(verdict,
                List.of(new ToolCallRecord("httpRequest", "{}", "HTTP 200", 0, Duration.ofMillis(3))),
                Duration.ofMillis(120));
    }

    @Test
    void recordsBothSidesOfTheAudit() {
        val recorder = new StepEvaluationRecorder();

        recorder.record(request(1), run(new StepVerdict(Outcome.PASS, "created", List.of("HTTP 200"))),
                new EvaluationResponse(true, 1.0f, "all grounded", Map.of("category", "GROUNDED")));

        assertThat(recorder.records()).singleElement().satisfies(record -> {
            assertThat(record.scenario()).isEqualTo("Transfer");
            assertThat(record.scenarioSource()).isEqualTo("classpath:scenarios/explicit/transfer.md");
            assertThat(record.scenarioGroup()).isEqualTo("TransferSuiteTest");
            assertThat(record.expectedOutcome()).isEqualTo(Outcome.PASS);
            assertThat(record.stepIndex()).isEqualTo(1);
            assertThat(record.stepCount()).isEqualTo(2);
            assertThat(record.stepTitle()).isEqualTo("Open");
            assertThat(record.outcome()).isEqualTo(Outcome.PASS);
            assertThat(record.reasoning()).isEqualTo("created");
            assertThat(record.evidence()).containsExactly("HTTP 200");
            assertThat(record.toolCalls()).singleElement().satisfies(call ->
                    assertThat(call).contains("httpRequest").contains("HTTP 200"));
            assertThat(record.elapsedMillis()).isEqualTo(120);
            assertThat(record.score()).isEqualTo(1.0);
            assertThat(record.category()).isEqualTo("GROUNDED");
            assertThat(record.feedback()).isEqualTo("all grounded");
            assertThat(record.evaluated()).isTrue();
        });
    }

    @Test
    void notEvaluatedRecordsAreExcludedFromTheMean() {
        val recorder = new StepEvaluationRecorder();
        recorder.record(request(1), run(new StepVerdict(Outcome.PASS, "ok", List.of())),
                new EvaluationResponse(true, 1.0f, "", Map.of("category", "GROUNDED")));
        recorder.record(request(2), run(new StepVerdict(Outcome.PASS, "ok", List.of())),
                new EvaluationResponse(false, 0.5f, "", Map.of("category", "PARTIALLY_GROUNDED")));
        recorder.recordNotEvaluated(request(2),
                run(new StepVerdict(Outcome.FAIL, "unparseable", List.of())),
                "Harness-synthesized verdict; not evaluated.");

        assertThat(recorder.overallScore()).hasValue(0.75);
        assertThat(recorder.records()).hasSize(3);
        assertThat(recorder.records().getLast().category())
                .isEqualTo(StepEvaluationRecord.NOT_EVALUATED);
        assertThat(recorder.records().getLast().evaluated()).isFalse();
    }

    @Test
    void overallScoreEmptyWithNoRecords() {
        assertThat(new StepEvaluationRecorder().overallScore()).isEmpty();
    }

    @Test
    void categoryNullWhenAbsentFromMetadata() {
        val recorder = new StepEvaluationRecorder();
        recorder.record(request(1), run(new StepVerdict(Outcome.FAIL, "no verdict", List.of())),
                new EvaluationResponse(false, 0.0f, "no verdict", Map.of()));

        assertThat(recorder.records()).singleElement()
                .satisfies(record -> assertThat(record.category()).isNull());
    }
}
