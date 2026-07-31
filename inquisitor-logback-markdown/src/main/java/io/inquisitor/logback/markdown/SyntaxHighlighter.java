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

package io.inquisitor.logback.markdown;

import java.util.List;
import java.util.Objects;

/**
 * Converts one source-code line into styled spans without changing its text.
 * Implementations must be thread-safe because a renderer may serve concurrent loggers.
 */
@FunctionalInterface
public interface SyntaxHighlighter {

    /**
     * Highlights one line from a fenced code block.
     *
     * @param language normalized fenced-code language, or an empty string
     * @param source exact source line
     * @return ordered spans whose concatenated text equals {@code source}
     */
    List<Span> highlight(String language, String source);

    /** Semantic token styles understood by the terminal renderer. */
    enum Style {
        PLAIN,
        KEYWORD,
        STRING,
        NUMBER,
        COMMENT,
        PROPERTY,
        VARIABLE,
        OPERATOR
    }

    /** An exact source fragment and its semantic style. */
    record Span(String text, Style style) {

        public Span {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
        }
    }
}
