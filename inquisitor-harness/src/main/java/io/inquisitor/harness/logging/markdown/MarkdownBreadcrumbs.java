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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.model.Scenario;
import lombok.val;

/** Shared Powerline-style breadcrumb construction for Markdown lifecycle logs. */
final class MarkdownBreadcrumbs {

    private static final int GAUGE_WIDTH = 5;
    private static final String GAUGE_FILLED = "▰";
    private static final String GAUGE_EMPTY = "▱";
    private static final String GGUF_EXTENSION = ".gguf";
    private static final String POWERLINE_LEFT_CAP = "";
    private static final String POWERLINE_SEPARATOR = "";
    private static final String POWERLINE_RIGHT_CAP = "";

    private MarkdownBreadcrumbs() { }

    static String scenario(
            Scenario scenario,
            int completedSteps,
            String... trailingSegments) {
        val segments = new ArrayList<String>();
        segments.add(scenario.name());
        segments.add(progress(completedSteps, scenario.steps().size()));
        segments.addAll(List.of(trailingSegments));
        return breadcrumb(segments);
    }

    static String step(
            StepRequest request,
            Optional<String> reportedModel,
            String... trailingSegments) {
        val scenario = request.scenario();
        val step = request.step();
        val segments = new ArrayList<String>();
        reportedModel.map(MarkdownBreadcrumbs::displayModelName).ifPresent(segments::add);
        segments.add(scenario.name());
        segments.add(stepTitle(step.title(), step.index()));
        segments.add(progress(step.index(), scenario.steps().size()));
        segments.addAll(List.of(trailingSegments));
        return breadcrumb(segments);
    }

    static String http(
            String target,
            String method,
            String path,
            String... trailingSegments) {
        val segments = new ArrayList<String>();
        segments.add("HTTP");
        segments.add(target);
        segments.add(method);
        segments.add(path);
        segments.addAll(List.of(trailingSegments));
        return breadcrumb(segments.stream()
                .map(MarkdownBreadcrumbs::headingLiteral)
                .toList());
    }

    static String sql(String datasource, String status) {
        return breadcrumb(List.of("SQL", datasource, status).stream()
                .map(MarkdownBreadcrumbs::headingLiteral)
                .toList());
    }

    private static String breadcrumb(List<String> segments) {
        return POWERLINE_LEFT_CAP + " "
                + String.join(" " + POWERLINE_SEPARATOR + " ", segments)
                + " " + POWERLINE_RIGHT_CAP;
    }

    private static String headingLiteral(String value) {
        return value.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]");
    }

    private static String progress(int current, int total) {
        return "%s %d/%d".formatted(progressGauge(current, total), current, total);
    }

    private static String stepTitle(String title, int index) {
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
