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

package io.inquisitor.harness.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import lombok.val;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests of the executor's orchestration, using a scripted
 * {@link StepRunner} — no LLM involved.
 */
class ScenarioExecutorTest {

    private static Scenario threeSteps() {
        return new Scenario(
                "Order lifecycle",
                "Exercises the order API.",
                List.of(
                        new Step(1, "Create", "create an order"),
                        new Step(2, "Load", "load the order"),
                        new Step(3, "Delete", "delete the order")),
                "order-lifecycle.md");
    }

    @Test
    void evaluatesEveryStepWhenAllPass() {
        var evaluator = new ScriptedEvaluator(ignored -> Outcome.PASS);
        var executor = new ScenarioExecutor(evaluator);

        ScenarioResult result = executor.execute(threeSteps());

        assertThat(result.passed()).isTrue();
        assertThat(result.results()).hasSize(3);
        assertThat(result.firstFailure()).isEmpty();
        assertThat(evaluator.evaluated).extracting(Step::index).containsExactly(1, 2, 3);
    }

    @Test
    void stopsAtFirstFailingStep() {
        var evaluator = new ScriptedEvaluator(step -> step.index() == 2 ? Outcome.FAIL : Outcome.PASS);
        var executor = new ScenarioExecutor(evaluator);

        ScenarioResult result = executor.execute(threeSteps());

        assertThat(result.passed()).isFalse();
        assertThat(result.results()).hasSize(2);
        assertThat(result.firstFailure()).isPresent()
                .get().extracting(r -> r.step().index()).isEqualTo(2);
        // step 3 must never be evaluated
        assertThat(evaluator.evaluated).extracting(Step::index).containsExactly(1, 2);
    }

    @Test
    void allStepsShareOneConversationId() {
        var evaluator = new ScriptedEvaluator(ignored -> Outcome.PASS);
        new ScenarioExecutor(evaluator).execute(threeSteps());

        assertThat(evaluator.conversationIds).hasSize(1);
    }

    @Test
    void singleStepScenarioPasses() {
        var scenario = new Scenario("Ping", "", List.of(new Step(1, "Ping", "ping it")), null);
        var result = new ScenarioExecutor(new ScriptedEvaluator(ignored -> Outcome.PASS)).execute(scenario);

        assertThat(result.passed()).isTrue();
        assertThat(result.results()).hasSize(1);
    }

    @Test
    void stepAtATimeApiStopsAfterFailure() {
        var evaluator = new ScriptedEvaluator(step -> step.index() == 2 ? Outcome.FAIL : Outcome.PASS);
        var evaluation = new ScenarioExecutor(evaluator).start(threeSteps());

        assertThat(evaluation.hasNext()).isTrue();
        assertThat(evaluation.next().step().index()).isEqualTo(1);
        assertThat(evaluation.hasNext()).isTrue();
        assertThat(evaluation.next().passed()).isFalse();
        assertThat(evaluation.hasNext()).isFalse();
        assertThat(evaluation.result().results()).hasSize(2);
    }

    @Test
    void wholeScenarioLogsScenarioOnceBeforeRunningSteps() {
        assertThat(executionOrder(executor -> executor.execute(threeSteps())))
                .containsExactly(
                        "scenario:Order lifecycle",
                        "run:Create",
                        "run:Load",
                        "run:Delete");
    }

    @Test
    void stepAtATimeExecutionUsesTheSameLifecycleOrdering() {
        assertThat(executionOrder(executor -> {
            val execution = executor.start(threeSteps());
            execution.next();
            execution.next();
        })).containsExactly(
                "scenario:Order lifecycle",
                "run:Create",
                "run:Load");
    }

    @Test
    void logsOneStartAndOneCompletionForAFailedScenario() {
        val events = new ArrayList<String>();
        val logger = new LifecycleScenarioLogger(events);
        val evaluator = new ScriptedEvaluator(step -> step.index() == 2 ? Outcome.FAIL : Outcome.PASS);

        val result = new ScenarioExecutor(evaluator, logger).execute(threeSteps());

        assertThat(result.passed()).isFalse();
        assertThat(events).containsExactly("scenario:Order lifecycle", "completed:FAIL");
    }

    @Test
    void logsPartialResultWhenExecutionIsAbortedByInfrastructureFailure() {
        val events = new ArrayList<String>();
        val logger = new LifecycleScenarioLogger(events);
        StepRunner runner = request -> {
            throw new IllegalStateException("transport unavailable");
        };
        val executor = new ScenarioExecutor(runner, logger);

        assertThatThrownBy(() -> executor.execute(threeSteps()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("transport unavailable");
        assertThat(events).containsExactly("scenario:Order lifecycle", "aborted:0");
    }

    @Test
    void scenarioCallbacksComposeInInvocationOrder() {
        val events = new ArrayList<String>();
        val first = new NamedScenarioCallback(events, "first");
        val second = new NamedScenarioCallback(events, "second");
        val result = new ScenarioResult(threeSteps(), List.of());
        val failure = new IllegalStateException("failed");
        val callback = first.andThen(second);

        callback.scenarioStarted(threeSteps());
        callback.scenarioAborted(result, failure);
        callback.scenarioCompleted(result);

        assertThat(events).containsExactly(
                "first:started", "second:started",
                "first:aborted", "second:aborted",
                "first:completed", "second:completed");
    }

    private static List<String> executionOrder(Consumer<ScenarioExecutor> invocation) {
        val order = new ArrayList<String>();
        StepRunner runner = request -> {
            order.add("run:" + request.step().title());
            return new StepRun(
                    new StepVerdict(Outcome.PASS, "scripted", List.of()),
                    List.of(), Duration.ZERO);
        };
        invocation.accept(new ScenarioExecutor(runner, new OrderingScenarioLogger(order)));
        return List.copyOf(order);
    }

    /** A {@link StepRunner} that returns scripted outcomes and records calls. */
    private static final class ScriptedEvaluator implements StepRunner {

        final List<Step> evaluated = new ArrayList<>();
        final Set<String> conversationIds = new LinkedHashSet<>();
        private final Function<Step, Outcome> script;

        ScriptedEvaluator(Function<Step, Outcome> script) {
            this.script = script;
        }

        @Override
        public StepRun run(StepRequest request) {
            evaluated.add(request.step());
            conversationIds.add(request.conversationId());
            return new StepRun(
                    new StepVerdict(script.apply(request.step()), "scripted", List.of("evidence")),
                    List.of(), Duration.ZERO);
        }
    }

    private static final class OrderingScenarioLogger implements ScenarioExecutionCallback {

        private final List<String> order;

        private OrderingScenarioLogger(List<String> order) {
            this.order = order;
        }

        @Override
        public void scenarioStarted(Scenario scenario) {
            order.add("scenario:" + scenario.name());
        }

        @Override
        public void scenarioAborted(ScenarioResult partialResult, Throwable cause) {
            order.add("aborted:" + partialResult.scenario().name());
        }

        @Override
        public void scenarioCompleted(ScenarioResult result) {
            // Completion is not relevant to the before-run ordering assertion.
        }

    }

    private static final class LifecycleScenarioLogger implements ScenarioExecutionCallback {

        private final List<String> events;

        private LifecycleScenarioLogger(List<String> events) {
            this.events = events;
        }

        @Override
        public void scenarioStarted(Scenario scenario) {
            events.add("scenario:" + scenario.name());
        }

        @Override
        public void scenarioAborted(ScenarioResult partialResult, Throwable cause) {
            events.add("aborted:" + partialResult.results().size());
        }

        @Override
        public void scenarioCompleted(ScenarioResult result) {
            events.add("completed:" + (result.passed() ? "PASS" : "FAIL"));
        }

    }

    private record NamedScenarioCallback(
            List<String> events,
            String name) implements ScenarioExecutionCallback {

        @Override
        public void scenarioStarted(Scenario scenario) {
            events.add(name + ":started");
        }

        @Override
        public void scenarioAborted(ScenarioResult partialResult, Throwable cause) {
            events.add(name + ":aborted");
        }

        @Override
        public void scenarioCompleted(ScenarioResult result) {
            events.add(name + ":completed");
        }
    }
}
