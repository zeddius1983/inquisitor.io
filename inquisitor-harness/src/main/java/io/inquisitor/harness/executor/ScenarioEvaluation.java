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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepResult;
import io.inquisitor.harness.model.StepVerdict;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * A stateful, step-at-a-time evaluation of a single {@link Scenario}.
 *
 * <p>Holds the conversation id (shared across steps) and accumulated results.
 * Evaluation is <strong>fail-fast</strong>: once a step fails, {@link #hasNext()}
 * returns {@code false} and no further steps run. Drives both the convenience
 * {@link ScenarioExecutor#evaluate(Scenario)} and the JUnit per-step runners.
 *
 * <p>Not thread-safe; intended to be driven from a single thread.
 */
@Slf4j
public class ScenarioEvaluation {

    private final StepEvaluator evaluator;
    private final Scenario scenario;
    private final String conversationId = UUID.randomUUID().toString();
    private final List<StepResult> results = new ArrayList<>();

    private int cursor = 0;
    private boolean failed = false;

    ScenarioEvaluation(StepEvaluator evaluator, Scenario scenario) {
        this.evaluator = evaluator;
        this.scenario = scenario;
    }

    /** Whether another step remains and no prior step has failed. */
    public boolean hasNext() {
        return !failed && cursor < scenario.steps().size();
    }

    /** Evaluates the next step, recording and returning its result. */
    public StepResult next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No further steps to evaluate for scenario: " + scenario.name());
        }
        val number = cursor + 1;
        val total = scenario.steps().size();
        val step = scenario.steps().get(cursor);
        log.debug("[{}] step {}/{} — evaluating: {}", scenario.name(), number, total, step.title());
        val startedNanos = System.nanoTime();
        val verdict = evaluator.evaluate(conversationId, scenario, step);
        val result = new StepResult(step, verdict, Duration.ofNanos(System.nanoTime() - startedNanos));
        results.add(result);
        cursor++;
        if (!verdict.passed()) {
            failed = true;
        }
        log.debug("[{}] step {}/{} — {} in {} ms: {}", scenario.name(), number, total,
                verdict.outcome(), result.elapsed().toMillis(), verdict.reasoning());
        return result;
    }

    /** The result so far (or final, once {@link #hasNext()} is {@code false}). */
    public ScenarioResult result() {
        return new ScenarioResult(scenario, results);
    }

    public String conversationId() {
        return conversationId;
    }

    public Scenario scenario() {
        return scenario;
    }
}
