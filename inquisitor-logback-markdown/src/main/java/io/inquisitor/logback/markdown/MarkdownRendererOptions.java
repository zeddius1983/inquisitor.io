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

/** Immutable terminal-layout options for the built-in Markdown renderer. */
public final class MarkdownRendererOptions {

    public static final int DEFAULT_TABLE_WIDTH = 120;
    public static final int MIN_TABLE_WIDTH = 20;
    public static final int MAX_TABLE_WIDTH = 1_000;

    private final int tableWidth;
    private final int tableIndent;

    /** Creates the documented default options. */
    public MarkdownRendererOptions() {
        this(DEFAULT_TABLE_WIDTH);
    }

    /**
     * Creates options with a deterministic message-body table width. Unsupported
     * values normalize to {@link #DEFAULT_TABLE_WIDTH}; converter integration also
     * reports invalid source options through Logback's status system.
     *
     * @param tableWidth maximum message-body width allocated to a table
     */
    public MarkdownRendererOptions(int tableWidth) {
        this(isValidTableWidth(tableWidth) ? tableWidth : DEFAULT_TABLE_WIDTH, 0);
    }

    private MarkdownRendererOptions(int tableWidth, int tableIndent) {
        this.tableWidth = tableWidth;
        this.tableIndent = tableIndent;
    }

    /** Maximum message-body width allocated to a table. */
    public int tableWidth() {
        return tableWidth;
    }

    /** Whether a message-body table width is within the supported safety bounds. */
    public static boolean isValidTableWidth(int tableWidth) {
        return tableWidth >= MIN_TABLE_WIDTH && tableWidth <= MAX_TABLE_WIDTH;
    }

    MarkdownRendererOptions withTableIndent(int indent) {
        return new MarkdownRendererOptions(tableWidth, Math.max(0, indent));
    }

    int tableIndent() {
        return tableIndent;
    }
}
