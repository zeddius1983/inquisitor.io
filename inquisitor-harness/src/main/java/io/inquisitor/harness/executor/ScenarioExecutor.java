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

import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import lombok.val;

/**
 * Executes {@link Scenario scenarios} step by step over a {@link StepRunner}.
 *
 * <p>Use {@link #execute(Scenario)} to run a whole scenario, or
 * {@link #start(Scenario)} to drive it one step at a time (e.g. to report each
 * step as its own JUnit test). Both share the same fail-fast semantics via
 * {@link ScenarioExecution}.
 */
public class ScenarioExecutor {

    private final StepRunner runner;

    public ScenarioExecutor(StepRunner runner) {
        this.runner = runner;
    }

    /** Executes every step, stopping at the first failure. */
    public ScenarioResult execute(Scenario scenario) {
        val execution = start(scenario);
        while (execution.hasNext()) {
            execution.next();
        }
        return execution.result();
    }

    /** Begins a step-at-a-time execution over a fresh conversation. */
    public ScenarioExecution start(Scenario scenario) {
        return new ScenarioExecution(runner, scenario);
    }
}
