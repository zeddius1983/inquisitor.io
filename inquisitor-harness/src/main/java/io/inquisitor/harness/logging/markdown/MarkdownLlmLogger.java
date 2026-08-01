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

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.logging.LlmLoggerCallback;
import io.inquisitor.harness.logging.LogDurationFormatter;
import io.inquisitor.harness.logging.ModelRegistry;
import io.inquisitor.harness.logging.ModelRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Marker-tagged Markdown actor-model diagnostics. */
@Slf4j
@RequiredArgsConstructor
public class MarkdownLlmLogger implements LlmLoggerCallback {

    private final ModelRegistry models;

    @Override
    public void stepStarted(StepRequest request) {
        val step = request.step();
        log.debug(MarkdownLogSupport.marker(), MarkdownLogSupport.block("""
                ### %s

                ## %s%s
                """.formatted(breadcrumb(request, "RUN"), step.title(),
                body(step.instruction()))));
    }

    @Override
    public void responseUnparseable(StepRequest request, Throwable cause) {
        val scenario = request.scenario();
        val step = request.step();
        log.debug(MarkdownLogSupport.marker(), MarkdownLogSupport.block("""
                > **Actor response rejected — step %d/%d:** unparseable model response; treating as FAIL.
                >
                > %s
                """.formatted(step.index(), scenario.steps().size(), safeMessage(cause))));
    }

    @Override
    public void stepCompleted(StepRequest request, StepRun run) {
        val breadcrumb = breadcrumb(request, run.verdict().outcome().name(),
                "⧖ " + LogDurationFormatter.format(run.elapsed()));
        log.debug(MarkdownLogSupport.marker(), MarkdownLogSupport.block("""
                ### %s

                ### Reasoning

                %s
                """.formatted(breadcrumb,
                MarkdownStepLogSupport.blockquote(
                        run.verdict().reasoning(), breadcrumb.length()))));
    }

    private static String safeMessage(Throwable cause) {
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private String breadcrumb(StepRequest request, String... trailingSegments) {
        return MarkdownStepLogSupport.breadcrumb(
                request, models.actualModel(ModelRole.ACTOR), trailingSegments);
    }

    private static String body(String instruction) {
        return instruction.isBlank() ? "" : "\n\n" + instruction;
    }
}
