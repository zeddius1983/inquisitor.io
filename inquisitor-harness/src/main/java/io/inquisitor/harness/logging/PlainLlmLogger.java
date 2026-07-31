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

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Conventional single-line actor-model diagnostics. */
@Slf4j
public class PlainLlmLogger implements LlmLoggerCallback {

    @Override
    public void stepStarted(StepRequest request) {
        val scenario = request.scenario();
        val step = request.step();
        log.debug("[{}] step {}/{} - RUN: {}{}", scenario.name(), step.index(),
                scenario.steps().size(), step.title(), body(step.instruction()));
    }

    @Override
    public void responseUnparseable(StepRequest request, Throwable cause) {
        val scenario = request.scenario();
        val step = request.step();
        log.debug("[{}] step {}/{} — unparseable model response, treating as FAIL: {}",
                scenario.name(), step.index(), scenario.steps().size(), cause.getMessage());
    }

    @Override
    public void stepCompleted(StepRequest request, StepRun run) {
        val scenario = request.scenario();
        val step = request.step();
        log.debug("[{}] step {}/{} — {} in {}: {}", scenario.name(), step.index(),
                scenario.steps().size(), run.verdict().outcome(),
                LogDurationFormatter.format(run.elapsed()), run.verdict().reasoning());
    }

    private static String body(String instruction) {
        return instruction.isBlank() ? "" : "\n" + instruction;
    }
}
