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
import java.util.concurrent.atomic.AtomicReference;

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.executor.StepRunner;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import io.inquisitor.harness.model.ToolCallRecord;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * The decorator is transparent to the verdict and grounds the judge on the real trace.
 */
class EvaluationStepRunnerTest {

    private static final Scenario SCENARIO =
            new Scenario("Deposit", "desc", List.of(new Step(1, "Open", "open and deposit 100")), "s.md");

    @Test
    void returnsVerdictUnchangedAndGroundsJudgeOnTheTrace() {
        val verdict = new StepVerdict(Outcome.PASS, "balance is 100", List.of("HTTP 200"));
        val toolCalls = List.of(
                new ToolCallRecord("httpRequest", "{\"path\":\"/accounts\"}", "HTTP 201", 0, Duration.ofMillis(3)),
                new ToolCallRecord("sqlQuery", "SELECT balance", "1 row(s): [{balance=100}]", 1, Duration.ofMillis(2)));
        val delegate = (StepRunner) request -> new StepRun(verdict, toolCalls);

        val captured = new AtomicReference<EvaluationRequest>();
        val evaluator = (Evaluator) request -> {
            captured.set(request);
            return new EvaluationResponse(true, 1.0f, "grounded", Map.of("category", "GROUNDED"));
        };
        val recorder = new StepEvaluationRecorder();
        val runner = new EvaluationStepRunner(delegate, evaluator, recorder);

        val run = runner.run(StepRequest.of("conv-1", SCENARIO, SCENARIO.steps().get(0)));

        // transparent to the verdict
        assertThat(run.verdict()).isSameAs(verdict);

        // the judge sees the step instruction, the real trace, and the actor's verdict
        val request = captured.get();
        assertThat(request.getUserText()).contains("open and deposit 100");
        assertThat(request.getDataList()).hasSize(1);
        assertThat(request.getDataList().getFirst().getText())
                .contains("httpRequest").contains("HTTP 201")
                .contains("sqlQuery").contains("balance=100");
        assertThat(request.getResponseContent())
                .contains("PASS").contains("balance is 100").contains("HTTP 200");

        // the score is recorded
        assertThat(recorder.records()).singleElement().satisfies(record -> {
            assertThat(record.score()).isEqualTo(1.0);
            assertThat(record.category()).isEqualTo("GROUNDED");
        });
    }

    @Test
    void passesEmptyContextWhenNoToolCalls() {
        val verdict = new StepVerdict(Outcome.PASS, "looks fine", List.of());
        val delegate = (StepRunner) request -> new StepRun(verdict, List.of());
        val captured = new AtomicReference<EvaluationRequest>();
        val evaluator = (Evaluator) request -> {
            captured.set(request);
            return new EvaluationResponse(false, 0.2f, "no calls", Map.of("category", "UNSUPPORTED"));
        };
        val runner = new EvaluationStepRunner(delegate, evaluator, new StepEvaluationRecorder());

        runner.run(StepRequest.of("conv-2", SCENARIO, SCENARIO.steps().get(0)));

        assertThat(captured.get().getDataList()).isEmpty();
    }
}
