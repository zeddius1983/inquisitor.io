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

package io.inquisitor.harness.evaluation.logging;

import java.time.Duration;

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.ai.evaluation.EvaluationResponse;

/** Conventional single-line judge-evaluation diagnostics. */
@Slf4j
public class PlainEvaluationLogger implements EvaluationLoggerCallback {

    @Override
    public void evaluationStarted(StepRequest request, StepRun actorRun) {
        val scenario = request.scenario();
        val step = request.step();
        log.debug("[{}] step {}/{} - EVALUATE: {}", scenario.name(), step.index(),
                scenario.steps().size(), step.title());
    }

    @Override
    public void evaluationSkipped(StepRequest request, StepRun actorRun, String reason) {
        val scenario = request.scenario();
        val step = request.step();
        log.debug("[{}] step {}/{} - NOT_EVALUATED: {}", scenario.name(), step.index(),
                scenario.steps().size(), reason);
    }

    @Override
    public void evaluationFailed(StepRequest request, StepRun actorRun, Throwable cause) {
        val scenario = request.scenario();
        val step = request.step();
        log.warn("[{}] step {}/{} - NOT_EVALUATED: the judge call failed",
                scenario.name(), step.index(), scenario.steps().size(), cause);
    }

    @Override
    public void evaluationCompleted(
            StepRequest request,
            StepRun actorRun,
            EvaluationResponse response,
            Duration elapsed) {
        val scenario = request.scenario();
        val step = request.step();
        val category = response.getMetadata() == null
                ? null
                : response.getMetadata().get("category");
        log.debug("[{}] step {}/{} - {}: score {}", scenario.name(), step.index(),
                scenario.steps().size(), category, response.getScore());
    }
}
