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

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.evaluation.EvaluationStepRunnerCallback;
import io.inquisitor.harness.logging.MarkdownLogSupport;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.ai.evaluation.EvaluationResponse;

/** Marker-tagged Markdown judge-evaluation diagnostics. */
@Slf4j
public class MarkdownEvaluationLogger implements EvaluationStepRunnerCallback {

    @Override
    public void evaluationStarted(StepRequest request, StepRun actorRun) {
        val scenario = request.scenario();
        val step = request.step();
        debug("""
                ### Judge evaluation — step %d/%d

                **Scenario:** %s
                **Step:** %s
                """.formatted(step.index(), scenario.steps().size(),
                scenario.name(), step.title()));
    }

    @Override
    public void evaluationSkipped(StepRequest request, StepRun actorRun, String reason) {
        val scenario = request.scenario();
        val step = request.step();
        debug("""
                > **Not evaluated — step %d/%d:** %s
                """.formatted(step.index(), scenario.steps().size(), reason));
    }

    @Override
    public void evaluationFailed(StepRequest request, StepRun actorRun, Throwable cause) {
        val scenario = request.scenario();
        val step = request.step();
        log.warn(MarkdownLogSupport.marker(), MarkdownLogSupport.block("""
                > **Judge call failed — step %d/%d:** the actor result is unchanged and this step was not evaluated.
                """.formatted(step.index(), scenario.steps().size())), cause);
    }

    @Override
    public void evaluationCompleted(
            StepRequest request,
            StepRun actorRun,
            EvaluationResponse response) {
        val scenario = request.scenario();
        val step = request.step();
        val category = response.getMetadata() == null
                ? "—"
                : String.valueOf(response.getMetadata().get("category"));
        debug("""
                ### Judge result — %s

                - **Step:** `%d/%d`
                - **Score:** `%.3f`
                - **Feedback:** %s
                """.formatted(category, step.index(), scenario.steps().size(),
                response.getScore(), response.getFeedback()));
    }

    private static void debug(String markdown) {
        log.debug(MarkdownLogSupport.marker(), MarkdownLogSupport.block(markdown));
    }
}
