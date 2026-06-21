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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import org.junit.jupiter.api.Test;

/**
 * Deterministic tests of the executor's orchestration, using a scripted
 * {@link StepEvaluator} — no LLM involved.
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

        ScenarioResult result = executor.evaluate(threeSteps());

        assertThat(result.passed()).isTrue();
        assertThat(result.results()).hasSize(3);
        assertThat(result.firstFailure()).isEmpty();
        assertThat(evaluator.evaluated).extracting(Step::index).containsExactly(1, 2, 3);
    }

    @Test
    void stopsAtFirstFailingStep() {
        var evaluator = new ScriptedEvaluator(step -> step.index() == 2 ? Outcome.FAIL : Outcome.PASS);
        var executor = new ScenarioExecutor(evaluator);

        ScenarioResult result = executor.evaluate(threeSteps());

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
        new ScenarioExecutor(evaluator).evaluate(threeSteps());

        assertThat(evaluator.conversationIds).hasSize(1);
    }

    @Test
    void singleStepScenarioPasses() {
        var scenario = new Scenario("Ping", "", List.of(new Step(1, "Ping", "ping it")), null);
        var result = new ScenarioExecutor(new ScriptedEvaluator(ignored -> Outcome.PASS)).evaluate(scenario);

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

    /** A {@link StepEvaluator} that returns scripted outcomes and records calls. */
    private static final class ScriptedEvaluator implements StepEvaluator {

        final List<Step> evaluated = new ArrayList<>();
        final Set<String> conversationIds = new LinkedHashSet<>();
        private final Function<Step, Outcome> script;

        ScriptedEvaluator(Function<Step, Outcome> script) {
            this.script = script;
        }

        @Override
        public StepVerdict evaluate(String conversationId, Scenario scenario, Step step) {
            evaluated.add(step);
            conversationIds.add(conversationId);
            return new StepVerdict(script.apply(step), "scripted", List.of("evidence"));
        }
    }
}
