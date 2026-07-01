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

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.model.StepResult;
import lombok.val;

/**
 * A stateful, step-at-a-time execution of a single {@link Scenario}.
 *
 * <p>Holds the conversation id (shared across steps) and accumulated results.
 * Execution is <strong>fail-fast</strong>: once a step fails, {@link #hasNext()}
 * returns {@code false} and no further steps run. Drives both the convenience
 * {@link ScenarioExecutor#execute(Scenario)} and the JUnit per-step runners.
 *
 * <p>Not thread-safe; intended to be driven from a single thread.
 */
public class ScenarioExecution {

    private final StepRunner runner;
    private final Scenario scenario;
    private final String conversationId = UUID.randomUUID().toString();
    private final List<StepResult> results = new ArrayList<>();

    private int cursor = 0;
    private boolean failed = false;

    ScenarioExecution(StepRunner runner, Scenario scenario) {
        this.runner = runner;
        this.scenario = scenario;
    }

    /** Whether another step remains and no prior step has failed. */
    public boolean hasNext() {
        return !failed && cursor < scenario.steps().size();
    }

    /** Executes the next step, recording and returning its result. */
    public StepResult next() {
        if (!hasNext()) {
            throw new NoSuchElementException("No further steps to execute for scenario: " + scenario.name());
        }
        val step = scenario.steps().get(cursor);
        val run = runner.run(StepRequest.of(conversationId, scenario, step));
        val result = new StepResult(step, run.verdict(), run.elapsed());
        results.add(result);
        cursor++;
        if (!run.verdict().passed()) {
            failed = true;
        }
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
