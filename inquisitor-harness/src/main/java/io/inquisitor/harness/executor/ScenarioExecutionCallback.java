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

/**
 * Receives lifecycle events from a {@link ScenarioExecution}.
 *
 * <p>Events follow calls that drive execution. Abandoning a step-at-a-time
 * execution before its terminal {@link ScenarioExecution#next()} call does not
 * synthesize completion or abort, and a scenario with no steps emits no event.
 */
public interface ScenarioExecutionCallback {

    /** Silent callback used when scenario lifecycle observation is not configured. */
    ScenarioExecutionCallback NO_OP = new ScenarioExecutionCallback() {
        @Override public void scenarioStarted(Scenario scenario) { }
        @Override public void scenarioAborted(ScenarioResult partialResult, Throwable cause) { }
        @Override public void scenarioCompleted(ScenarioResult result) { }
    };

    /** Invoked when scenario execution starts. */
    void scenarioStarted(Scenario scenario);

    /** Invoked when an infrastructure exception interrupts scenario execution. */
    void scenarioAborted(ScenarioResult partialResult, Throwable cause);

    /** Invoked when a scenario completes normally, including verdict failures. */
    void scenarioCompleted(ScenarioResult result);
}
