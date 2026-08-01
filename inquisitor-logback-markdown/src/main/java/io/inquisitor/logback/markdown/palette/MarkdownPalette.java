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

package io.inquisitor.logback.markdown.palette;

/**
 * Semantic terminal colors used by Markdown and Powerline rendering.
 *
 * <p>RGB values use the unsigned {@code 0xRRGGBB} form. Implementations should
 * be immutable and thread-safe because one palette can be shared by concurrent
 * log rendering calls.
 */
public interface MarkdownPalette {

    /** Primary foreground used on dark surfaces and for ordinary styled text. */
    int foreground();

    /** Dark surface used for timestamp segments and code-block backgrounds. */
    int surface();

    /** Muted neutral used for duration segments, comments, and table borders. */
    int muted();

    /** Blue accent used for links and properties. */
    int blue();

    /** Aqua accent used for headings and progress. */
    int aqua();

    /** Green accent used for success states and list markers. */
    int green();

    /** Orange accent used for scenario segments and quote markers. */
    int orange();

    /** Purple accent used for debug states and inline code. */
    int purple();

    /** Red accent used for failures. */
    int red();

    /** Yellow accent used for steps and warnings. */
    int yellow();
}
