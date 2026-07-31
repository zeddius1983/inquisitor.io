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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.inquisitor.harness.executor.StepRequest;
import lombok.val;

/** Shared Markdown formatting for actor and judge step diagnostics. */
public final class MarkdownStepLogSupport {

    private static final int GAUGE_WIDTH = 5;
    private static final String GAUGE_FILLED = "▰";
    private static final String GAUGE_EMPTY = "▱";
    private static final String GGUF_EXTENSION = ".gguf";
    private static final int BLOCKQUOTE_PREFIX_WIDTH = 2;

    private MarkdownStepLogSupport() { }

    /** Builds the common model/scenario/step/progress breadcrumb. */
    public static String breadcrumb(
            StepRequest request,
            Optional<String> reportedModel,
            String... trailingSegments) {
        val scenario = request.scenario();
        val step = request.step();
        val total = scenario.steps().size();
        val tail = String.join("  ", trailingSegments);
        val model = reportedModel
                .map(MarkdownStepLogSupport::displayModelName)
                .map(name -> name + "  ")
                .orElse("");
        return " %s%s  %s  %s %d/%d  %s ".formatted(
                model, scenario.name(), breadcrumbTitle(step.title(), step.index()),
                progressGauge(step.index(), total), step.index(), total, tail);
    }

    /** Renders and wraps text as a Markdown blockquote within the given line width. */
    public static String blockquote(String value, int lineWidth) {
        val contentWidth = Math.max(1, lineWidth - BLOCKQUOTE_PREFIX_WIDTH);
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        val wrapped = new ArrayList<String>();
        for (String line : normalized.split("\n", -1)) {
            wrapped.addAll(wrapLine(line, contentWidth));
        }
        return String.join("\n", wrapped.stream().map(line -> "> " + line).toList());
    }

    private static List<String> wrapLine(String line, int width) {
        if (line.length() <= width) {
            return List.of(line);
        }
        val stripped = line.strip();
        if (stripped.isEmpty()) {
            return List.of("");
        }
        val wrapped = new ArrayList<String>();
        val current = new StringBuilder();
        for (String word : stripped.split("\\s+")) {
            if (!current.isEmpty() && current.length() + 1 + word.length() > width) {
                wrapped.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            wrapped.add(current.toString());
        }
        return List.copyOf(wrapped);
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
}
