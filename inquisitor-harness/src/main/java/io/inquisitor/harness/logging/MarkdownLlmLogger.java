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
import io.inquisitor.harness.executor.LlmStepRunnerCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Marker-tagged Markdown actor-model diagnostics. */
@Slf4j
@RequiredArgsConstructor
public class MarkdownLlmLogger implements LlmStepRunnerCallback {

    private static final int GAUGE_WIDTH = 5;
    private static final String GAUGE_FILLED = "▰";
    private static final String GAUGE_EMPTY = "▱";
    private static final String GGUF_EXTENSION = ".gguf";

    private final ModelRegistry models;

    @Override
    public void stepStarted(StepRequest request) {
        val step = request.step();
        log.debug(MarkdownLogSupport.marker(), MarkdownLogSupport.block("""
                ### %s

                ## %s%s
                """.formatted(breadcrumb(request, "Running"), step.title(),
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
        log.debug(MarkdownLogSupport.marker(), MarkdownLogSupport.block("""
                ### %s

                ### Reasoning

                %s
                """.formatted(breadcrumb(request, run.verdict().outcome().name(),
                "⧖ " + LogDurationFormatter.format(run.elapsed())),
                blockquote(run.verdict().reasoning()))));
    }

    private static String safeMessage(Throwable cause) {
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static String blockquote(String value) {
        return "> " + value.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "\n> ");
    }

    private static String breadcrumbTitle(String title, int index) {
        val prefix = "Step " + index;
        if (!title.regionMatches(true, 0, prefix, 0, prefix.length())
                || title.length() == prefix.length()) {
            return title;
        }
        int cursor = prefix.length();
        while (cursor < title.length() && Character.isWhitespace(title.charAt(cursor))) {
            cursor++;
        }
        if (cursor == title.length() || !isHeadingSeparator(title.charAt(cursor))) {
            return title;
        }
        cursor++;
        while (cursor < title.length() && Character.isWhitespace(title.charAt(cursor))) {
            cursor++;
        }
        return cursor == title.length() ? title : title.substring(cursor);
    }

    private static boolean isHeadingSeparator(char candidate) {
        return candidate == '—' || candidate == '–' || candidate == '-' || candidate == ':';
    }

    private String breadcrumb(StepRequest request, String... trailingSegments) {
        val scenario = request.scenario();
        val step = request.step();
        val total = scenario.steps().size();
        val tail = String.join("  ", trailingSegments);
        val model = models.actualModel(ModelRole.ACTOR)
                .map(MarkdownLlmLogger::displayModelName)
                .map(name -> name + "  ")
                .orElse("");
        return " %s%s  %s  %s %d/%d  %s ".formatted(
                model, scenario.name(), breadcrumbTitle(step.title(), step.index()),
                progressGauge(step.index(), total), step.index(), total, tail);
    }

    private static String displayModelName(String reported) {
        val separator = Math.max(reported.lastIndexOf('/'), reported.lastIndexOf('\\'));
        val filename = separator >= 0 && separator + 1 < reported.length()
                ? reported.substring(separator + 1)
                : reported;
        return filename.length() > GGUF_EXTENSION.length()
                && filename.regionMatches(true, filename.length() - GGUF_EXTENSION.length(),
                GGUF_EXTENSION, 0, GGUF_EXTENSION.length())
                ? filename.substring(0, filename.length() - GGUF_EXTENSION.length())
                : filename;
    }

    private static String progressGauge(int current, int total) {
        if (total <= 0) {
            return GAUGE_EMPTY.repeat(GAUGE_WIDTH);
        }
        val scaled = (long) current * GAUGE_WIDTH + total / 2L;
        val filled = (int) Math.min(GAUGE_WIDTH, Math.max(0L, scaled / total));
        return GAUGE_FILLED.repeat(filled) + GAUGE_EMPTY.repeat(GAUGE_WIDTH - filled);
    }

    private static String body(String instruction) {
        return instruction.isBlank() ? "" : "\n\n" + instruction;
    }
}
