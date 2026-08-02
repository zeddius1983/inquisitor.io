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

package io.inquisitor.logback.markdown.renderer;

import java.util.Objects;

import io.inquisitor.logback.markdown.ansi.AnsiPolicy;
import io.inquisitor.logback.markdown.ansi.PowerlineSegmentClassifier;
import io.inquisitor.logback.markdown.highlight.BuiltinSyntaxHighlighter;
import io.inquisitor.logback.markdown.highlight.SyntaxHighlighter;
import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPalettes;

/** Immutable policy for the built-in Markdown renderer. */
public record MarkdownRendererOptions(
        AnsiPolicy ansiPolicy,
        SyntaxHighlighter syntaxHighlighter,
        MarkdownPalette palette,
        PowerlineSegmentClassifier powerlineClassifier,
        int tableWidth,
        int tableIndent) {

    public static final int DEFAULT_TABLE_WIDTH = 120;
    public static final int MIN_TABLE_WIDTH = 20;
    public static final int MAX_TABLE_WIDTH = 1_000;

    /** Validates collaborators and normalizes terminal-layout dimensions. */
    public MarkdownRendererOptions {
        Objects.requireNonNull(ansiPolicy, "ansiPolicy");
        Objects.requireNonNull(syntaxHighlighter, "syntaxHighlighter");
        palette = MarkdownPalettes.validate(Objects.requireNonNull(palette, "palette"));
        Objects.requireNonNull(powerlineClassifier, "powerlineClassifier");
        tableWidth = isValidTableWidth(tableWidth) ? tableWidth : DEFAULT_TABLE_WIDTH;
        tableIndent = Math.max(0, tableIndent);
    }

    /** Returns the documented renderer defaults with automatic ANSI detection. */
    public static MarkdownRendererOptions defaults() {
        return new MarkdownRendererOptions(
                AnsiPolicy.DETECT,
                BuiltinSyntaxHighlighter.INSTANCE,
                MarkdownPalettes.defaultPalette(),
                PowerlineSegmentClassifier.standard(),
                DEFAULT_TABLE_WIDTH,
                0);
    }

    /** Returns options that always produce plain terminal text. */
    public static MarkdownRendererOptions plain() {
        return defaults().withAnsiPolicy(AnsiPolicy.NEVER);
    }

    /** Returns options that always emit ANSI terminal styling. */
    public static MarkdownRendererOptions ansi() {
        return defaults().withAnsiPolicy(AnsiPolicy.ALWAYS);
    }

    /** Whether a table width is within the supported safety bounds. */
    public static boolean isValidTableWidth(int tableWidth) {
        return tableWidth >= MIN_TABLE_WIDTH && tableWidth <= MAX_TABLE_WIDTH;
    }

    public MarkdownRendererOptions withAnsiPolicy(AnsiPolicy policy) {
        return new MarkdownRendererOptions(policy, syntaxHighlighter, palette,
                powerlineClassifier, tableWidth, tableIndent);
    }

    public MarkdownRendererOptions withSyntaxHighlighter(SyntaxHighlighter highlighter) {
        return new MarkdownRendererOptions(ansiPolicy, highlighter, palette,
                powerlineClassifier, tableWidth, tableIndent);
    }

    public MarkdownRendererOptions withPalette(MarkdownPalette value) {
        return new MarkdownRendererOptions(ansiPolicy, syntaxHighlighter, value,
                powerlineClassifier, tableWidth, tableIndent);
    }

    public MarkdownRendererOptions withPowerlineClassifier(
            PowerlineSegmentClassifier classifier) {
        return new MarkdownRendererOptions(ansiPolicy, syntaxHighlighter, palette,
                classifier, tableWidth, tableIndent);
    }

    public MarkdownRendererOptions withTableWidth(int width) {
        return new MarkdownRendererOptions(ansiPolicy, syntaxHighlighter, palette,
                powerlineClassifier, width, tableIndent);
    }

    public MarkdownRendererOptions withTableIndent(int indent) {
        return new MarkdownRendererOptions(ansiPolicy, syntaxHighlighter, palette,
                powerlineClassifier, tableWidth, indent);
    }
}
