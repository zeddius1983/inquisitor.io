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

import lombok.val;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** Shared marker protocol and block spacing for harness Markdown events. */
public final class MarkdownLogSupport {

    /** SLF4J marker protocol understood by the optional Markdown renderers. */
    public static final String MARKER_NAME = "INQUISITOR_MARKDOWN";

    private static final Marker MARKER = MarkerFactory.getMarker(MARKER_NAME);

    private MarkdownLogSupport() {
    }

    /** Returns the shared Markdown marker. */
    public static Marker marker() {
        return MARKER;
    }

    /** Adds visual separation from the surrounding conventional log stream. */
    public static String block(String markdown) {
        return "\n\n" + markdown.strip() + "\n";
    }

    static String codeBlock(String language, String content) {
        val fence = "`".repeat(Math.max(3, longestBacktickRun(content) + 1));
        return fence + language + "\n" + content + "\n" + fence;
    }

    private static int longestBacktickRun(String content) {
        int longest = 0;
        int current = 0;
        for (int index = 0; index < content.length(); index++) {
            if (content.charAt(index) == '`') {
                current++;
                longest = Math.max(longest, current);
            }
            else {
                current = 0;
            }
        }
        return longest;
    }
}
