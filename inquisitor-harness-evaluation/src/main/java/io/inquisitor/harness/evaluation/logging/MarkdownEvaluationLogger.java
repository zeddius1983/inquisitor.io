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
import io.inquisitor.harness.logging.LogDurationFormatter;
import io.inquisitor.harness.logging.ModelRegistry;
import io.inquisitor.harness.logging.ModelRole;
import io.inquisitor.harness.logging.markdown.MarkdownLogSupport;
import io.inquisitor.harness.logging.markdown.MarkdownStepLogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.ai.evaluation.EvaluationResponse;

/** Marker-tagged Markdown judge-evaluation diagnostics. */
@Slf4j
@RequiredArgsConstructor
public class MarkdownEvaluationLogger implements EvaluationLoggerCallback {

    private final ModelRegistry models;

    @Override
    public void evaluationStarted(StepRequest request, StepRun actorRun) {
        debug("### " + breadcrumb(request, "EVALUATION"));
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
            EvaluationResponse response,
            Duration elapsed) {
        val category = category(response);
        val breadcrumb = breadcrumb(request, category,
                "Score %.3f".formatted(response.getScore()),
                "⧖ " + LogDurationFormatter.format(elapsed));
        debug("""
                ### %s

                ### Feedback

                %s
                """.formatted(breadcrumb,
                MarkdownStepLogSupport.blockquote(
                        response.getFeedback(), breadcrumb.length())));
    }

    private String breadcrumb(StepRequest request, String... trailingSegments) {
        return MarkdownStepLogSupport.breadcrumb(
                request, models.actualModel(ModelRole.JUDGE), trailingSegments);
    }

    private static String category(EvaluationResponse response) {
        val metadata = response.getMetadata();
        return metadata == null || metadata.get("category") == null
                ? "—"
                : String.valueOf(metadata.get("category"));
    }

    private static void debug(String markdown) {
        log.debug(MarkdownLogSupport.marker(), MarkdownLogSupport.block(markdown));
    }
}
