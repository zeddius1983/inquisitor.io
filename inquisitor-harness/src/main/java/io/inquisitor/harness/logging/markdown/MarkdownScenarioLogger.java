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

package io.inquisitor.harness.logging.markdown;

import java.time.Duration;

import io.inquisitor.harness.executor.ScenarioExecutionCallback;
import io.inquisitor.harness.logging.LogDurationFormatter;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.model.StepResult;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Marker-tagged Markdown scenario lifecycle logs. */
@Slf4j
public class MarkdownScenarioLogger implements ScenarioExecutionCallback {

    @Override
    public void scenarioStarted(Scenario scenario) {
        val event = MarkdownLogSupport.event(MarkdownBreadcrumbs.scenario(scenario, 0, "RUN"))
                .content("# %s%s".formatted(
                        MarkdownBreadcrumbs.headingLiteral(scenario.name()),
                        body(scenario.description())));
        log.info(MarkdownLogSupport.marker(), event.message());
    }

    @Override
    public void scenarioAborted(ScenarioResult partialResult, Throwable cause) {
        val markdown = MarkdownLogSupport.block("""
                > **Scenario aborted:** %s
                > Completed `%d/%d` steps before an infrastructure failure.
                """.formatted(MarkdownBreadcrumbs.headingLiteral(
                        partialResult.scenario().name()), partialResult.results().size(),
                partialResult.scenario().steps().size()));
        log.atInfo()
                .addMarker(MarkdownLogSupport.marker())
                .setCause(cause)
                .log(markdown);
    }

    @Override
    public void scenarioCompleted(ScenarioResult result) {
        val outcome = result.passed() ? "PASS" : "FAIL";
        val breadcrumb = MarkdownBreadcrumbs.scenario(result.scenario(), result.results().size(),
                outcome, "⧖ " + LogDurationFormatter.format(elapsed(result)));
        log.info(MarkdownLogSupport.marker(), MarkdownLogSupport.event(breadcrumb).message());
    }

    private static Duration elapsed(ScenarioResult result) {
        return result.results().stream()
                .map(StepResult::elapsed)
                .reduce(Duration.ZERO, Duration::plus);
    }

    private static String body(String value) {
        return value.isBlank() ? "" : "\n\n" + value;
    }

}
