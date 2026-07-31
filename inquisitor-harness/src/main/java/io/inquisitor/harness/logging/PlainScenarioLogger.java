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

package io.inquisitor.harness.logging;

import io.inquisitor.harness.executor.ScenarioExecutionCallback;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import lombok.extern.slf4j.Slf4j;

/** Conventional plain-text scenario lifecycle logs. */
@Slf4j
public class PlainScenarioLogger implements ScenarioExecutionCallback {

    @Override
    public void scenarioStarted(Scenario scenario) {
        log.info("Scenario START: {}{}", scenario.name(), body(scenario.description()));
    }

    @Override
    public void scenarioAborted(ScenarioResult partialResult, Throwable cause) {
        log.warn("Scenario ABORTED: {} after {}/{} steps", partialResult.scenario().name(),
                partialResult.results().size(), partialResult.scenario().steps().size(), cause);
    }

    @Override
    public void scenarioCompleted(ScenarioResult result) {
        log.info("Scenario END: {} — {} ({}/{} steps)", result.scenario().name(),
                result.passed() ? "PASS" : "FAIL", result.results().size(),
                result.scenario().steps().size());
    }

    private static String body(String value) {
        return value.isBlank() ? "" : "\n" + value;
    }
}
