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
import lombok.val;
import org.jspecify.annotations.Nullable;

/** Shared Markdown formatting for actor and judge step diagnostics. */
public final class MarkdownStepLogSupport {

    private static final int BLOCKQUOTE_PREFIX_WIDTH = 2;

    private MarkdownStepLogSupport() { }

    /** Builds the common model/scenario/step/progress breadcrumb. */
    public static String breadcrumb(
            StepRequest request,
            Optional<String> reportedModel,
            String... trailingSegments) {
        return MarkdownBreadcrumbs.step(request, reportedModel, trailingSegments);
    }

    /** Renders and wraps text as a Markdown blockquote within the given line width. */
    public static String blockquote(@Nullable String value, int lineWidth) {
        val contentWidth = Math.max(1, lineWidth - BLOCKQUOTE_PREFIX_WIDTH);
        val normalized = (value == null ? "" : value)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
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

}
