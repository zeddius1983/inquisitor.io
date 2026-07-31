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
import lombok.val;

/** Marker-tagged Markdown scenario lifecycle logs. */
@Slf4j
public class MarkdownScenarioLogger implements ScenarioExecutionCallback {

    @Override
    public void scenarioStarted(Scenario scenario) {
        info(markdown("# ", scenario.name(), scenario.description()));
    }

    @Override
    public void scenarioAborted(ScenarioResult partialResult, Throwable cause) {
        val message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        log.warn(MarkdownLogSupport.marker(), MarkdownLogSupport.block("""
                > **Scenario aborted:** %s
                > Completed `%d/%d` steps before `%s`.
                """.formatted(partialResult.scenario().name(), partialResult.results().size(),
                partialResult.scenario().steps().size(), message)), cause);
    }

    @Override
    public void scenarioCompleted(ScenarioResult result) {
        info("""
                # Scenario result — %s

                **%s** completed `%d/%d` steps.
                """.formatted(result.passed() ? "PASS" : "FAIL", result.scenario().name(),
                result.results().size(), result.scenario().steps().size()));
    }

    private static String markdown(String prefix, String heading, String body) {
        return body.isBlank() ? prefix + heading : prefix + heading + "\n\n" + body;
    }

    private static void info(String markdown) {
        log.info(MarkdownLogSupport.marker(), MarkdownLogSupport.block(markdown));
    }
}
