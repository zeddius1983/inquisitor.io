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

import java.util.List;
import java.util.Map;

import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.evaluation.EvaluationResponse;

class StepEvaluationRecorderTest {

    private static final Scenario SCENARIO =
            new Scenario("Transfer", "desc", List.of(new Step(1, "t", "i")), "s.md");

    @Test
    void recordsScoreCategoryAndFeedback() {
        val recorder = new StepEvaluationRecorder();

        recorder.record(SCENARIO, new Step(1, "Open", "open it"),
                new EvaluationResponse(true, 1.0f, "all grounded", Map.of("category", "GROUNDED")));

        assertThat(recorder.records()).singleElement().satisfies(record -> {
            assertThat(record.scenario()).isEqualTo("Transfer");
            assertThat(record.stepIndex()).isEqualTo(1);
            assertThat(record.stepTitle()).isEqualTo("Open");
            assertThat(record.score()).isEqualTo(1.0);
            assertThat(record.category()).isEqualTo("GROUNDED");
            assertThat(record.feedback()).isEqualTo("all grounded");
        });
    }

    @Test
    void overallScoreIsTheMean() {
        val recorder = new StepEvaluationRecorder();
        recorder.record(SCENARIO, new Step(1, "a", "i"),
                new EvaluationResponse(true, 1.0f, "", Map.of()));
        recorder.record(SCENARIO, new Step(2, "b", "i"),
                new EvaluationResponse(false, 0.5f, "", Map.of()));

        assertThat(recorder.overallScore()).hasValue(0.75);
    }

    @Test
    void overallScoreEmptyWithNoRecords() {
        assertThat(new StepEvaluationRecorder().overallScore()).isEmpty();
    }

    @Test
    void categoryNullWhenAbsentFromMetadata() {
        val recorder = new StepEvaluationRecorder();
        recorder.record(SCENARIO, new Step(1, "a", "i"),
                new EvaluationResponse(false, 0.0f, "no verdict", Map.of()));

        assertThat(recorder.records()).singleElement()
                .satisfies(record -> assertThat(record.category()).isNull());
    }
}
