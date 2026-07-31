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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
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
        val delegate = (StepRunner) request -> new StepRun(verdict, toolCalls, Duration.ZERO);

        val captured = new AtomicReference<EvaluationRequest>();
        val evaluator = (Evaluator) request -> {
            captured.set(request);
            return new EvaluationResponse(true, 1.0f, "grounded", Map.of("category", "GROUNDED"));
        };
        val recorder = new StepEvaluationRecorder();
        val logger = new RecordingEvaluationLogger();
        val runner = new EvaluationStepRunner(delegate, evaluator, recorder.andThen(logger));

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

        // the score is recorded, with both sides of the audit
        assertThat(recorder.records()).singleElement().satisfies(record -> {
            assertThat(record.score()).isEqualTo(1.0);
            assertThat(record.category()).isEqualTo("GROUNDED");
            assertThat(record.outcome()).isEqualTo(Outcome.PASS);
            assertThat(record.toolCalls()).hasSize(2);
        });
        assertThat(logger.events).containsExactly("started:Open", "completed:GROUNDED");
    }

    @Test
    void judgeFailureNeverFailsTheStep() {
        val verdict = new StepVerdict(Outcome.PASS, "fine", List.of("HTTP 200"));
        val delegate = (StepRunner) request -> new StepRun(verdict, List.of(), Duration.ZERO);
        val evaluator = (Evaluator) request -> {
            throw new IllegalStateException("judge timed out");
        };
        val recorder = new StepEvaluationRecorder();
        val logger = new RecordingEvaluationLogger();
        val runner = new EvaluationStepRunner(delegate, evaluator, recorder.andThen(logger));

        val run = runner.run(StepRequest.of("conv-4", SCENARIO, SCENARIO.steps().get(0)));

        // the judge is an observer: its failure must not propagate, the verdict stands
        assertThat(run.verdict()).isSameAs(verdict);
        assertThat(recorder.records()).singleElement().satisfies(record -> {
            assertThat(record.category()).isEqualTo(StepEvaluationRecord.NOT_EVALUATED);
            assertThat(record.feedback()).contains("judge call failed").contains("judge timed out");
        });
        assertThat(recorder.overallScore()).isEmpty();
        assertThat(logger.events).containsExactly("started:Open", "failed:judge timed out");
    }

    @Test
    void syntheticVerdictSkipsTheJudge() {
        val verdict = new StepVerdict(Outcome.FAIL,
                "The model returned an empty or unparseable response.", List.of());
        val delegate = (StepRunner) request ->
                new StepRun(verdict, List.of(), Duration.ZERO, true);
        val evaluator = (Evaluator) request -> {
            throw new AssertionError("the judge must not be called for a synthetic verdict");
        };
        val recorder = new StepEvaluationRecorder();
        val logger = new RecordingEvaluationLogger();
        val runner = new EvaluationStepRunner(delegate, evaluator, recorder.andThen(logger));

        val run = runner.run(StepRequest.of("conv-3", SCENARIO, SCENARIO.steps().get(0)));

        assertThat(run.verdict()).isSameAs(verdict);
        assertThat(recorder.records()).singleElement().satisfies(record -> {
            assertThat(record.category()).isEqualTo(StepEvaluationRecord.NOT_EVALUATED);
            assertThat(record.evaluated()).isFalse();
        });
        assertThat(recorder.overallScore()).isEmpty();
        assertThat(logger.events).containsExactly(
                "skipped:Harness-synthesized verdict (no actor claim to audit); not evaluated.");
    }

    @Test
    void passesEmptyContextWhenNoToolCalls() {
        val verdict = new StepVerdict(Outcome.PASS, "looks fine", List.of());
        val delegate = (StepRunner) request -> new StepRun(verdict, List.of(), Duration.ZERO);
        val captured = new AtomicReference<EvaluationRequest>();
        val evaluator = (Evaluator) request -> {
            captured.set(request);
            return new EvaluationResponse(false, 0.2f, "no calls", Map.of("category", "UNSUPPORTED"));
        };
        val runner = new EvaluationStepRunner(delegate, evaluator, new StepEvaluationRecorder());

        runner.run(StepRequest.of("conv-2", SCENARIO, SCENARIO.steps().get(0)));

        assertThat(captured.get().getDataList()).isEmpty();
    }

    @Test
    void callbackCompositionInvokesCallbacksInOrderAndStopsOnFailure() {
        val events = new ArrayList<String>();
        val first = new OrderedEvaluationCallback("first", events, false);
        val second = new OrderedEvaluationCallback("second", events, false);
        val third = new OrderedEvaluationCallback("third", events, false);
        val callback = first.andThen(second).andThen(third);
        val request = StepRequest.of("conv", SCENARIO, SCENARIO.steps().getFirst());
        val run = new StepRun(
                new StepVerdict(Outcome.PASS, "done", List.of()), List.of(), Duration.ZERO);
        val cause = new IllegalStateException("judge failed");
        val response = new EvaluationResponse(
                true, 1.0f, "grounded", Map.of("category", "GROUNDED"));

        callback.evaluationStarted(request, run);
        callback.evaluationSkipped(request, run, "not evaluated");
        callback.evaluationFailed(request, run, cause);
        callback.evaluationCompleted(request, run, response, Duration.ofMillis(9_807));

        assertThat(events).containsExactly(
                "first:started", "second:started", "third:started",
                "first:skipped", "second:skipped", "third:skipped",
                "first:failed", "second:failed", "third:failed",
                "first:completed", "second:completed", "third:completed");

        events.clear();
        val failing = new OrderedEvaluationCallback("failing", events, true)
                .andThen(new OrderedEvaluationCallback("after", events, false));

        assertThatThrownBy(() -> failing.evaluationStarted(request, run))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("callback failed");
        assertThat(events).containsExactly("failing:started");
    }

    private static final class RecordingEvaluationLogger implements EvaluationStepRunnerCallback {

        private final List<String> events = new ArrayList<>();

        @Override
        public void evaluationStarted(StepRequest request, StepRun actorRun) {
            events.add("started:" + request.step().title());
        }

        @Override
        public void evaluationSkipped(StepRequest request, StepRun actorRun, String reason) {
            events.add("skipped:" + reason);
        }

        @Override
        public void evaluationFailed(StepRequest request, StepRun actorRun, Throwable cause) {
            events.add("failed:" + cause.getMessage());
        }

        @Override
        public void evaluationCompleted(
                StepRequest request,
                StepRun actorRun,
                EvaluationResponse response,
                Duration elapsed) {
            events.add("completed:" + response.getMetadata().get("category"));
        }
    }

    private record OrderedEvaluationCallback(
            String name,
            List<String> events,
            boolean failOnStart) implements EvaluationStepRunnerCallback {

        @Override
        public void evaluationStarted(StepRequest request, StepRun actorRun) {
            events.add(name + ":started");
            if (failOnStart) {
                throw new IllegalStateException("callback failed");
            }
        }

        @Override
        public void evaluationSkipped(StepRequest request, StepRun actorRun, String reason) {
            events.add(name + ":skipped");
        }

        @Override
        public void evaluationFailed(StepRequest request, StepRun actorRun, Throwable cause) {
            events.add(name + ":failed");
        }

        @Override
        public void evaluationCompleted(
                StepRequest request,
                StepRun actorRun,
                EvaluationResponse response,
                Duration elapsed) {
            events.add(name + ":completed");
        }
    }
}
