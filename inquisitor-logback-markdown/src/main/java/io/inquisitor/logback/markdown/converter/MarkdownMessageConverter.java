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

package io.inquisitor.logback.markdown.converter;

import java.util.List;
import java.util.regex.Pattern;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.inquisitor.logback.markdown.ansi.AnsiSupport;
import io.inquisitor.logback.markdown.marker.MarkdownMarkers;
import io.inquisitor.logback.markdown.renderer.FlexmarkAnsiRenderer;
import io.inquisitor.logback.markdown.renderer.MarkdownRenderer;
import io.inquisitor.logback.markdown.renderer.MarkdownRendererOptions;
import org.jspecify.annotations.Nullable;

/**
 * Logback pattern converter for {@code %mdMsg}, {@code %mdMsg{marked}}, and the
 * optional {@code plain}/{@code ansi}, {@code palette}, and table-width policies.
 */
public final class MarkdownMessageConverter extends MessageConverter {

    private static final String LEFT_PADDING = "    ";
    private static final Pattern NON_EMPTY_LINE_START = Pattern.compile("(?m)^(?=.)");

    private static final String MARKED_OPTION = "marked";
    private static final String PLAIN_OPTION = "plain";
    private static final String ANSI_OPTION = "ansi";
    private static final String TABLE_WIDTH_OPTION = "tableWidth";

    private final @Nullable MarkdownRenderer configuredRenderer;
    private final boolean configuredMarkedOnly;
    private volatile MarkdownRenderer renderer;
    private volatile boolean markedOnly;

    /** Creates a converter backed by the default Flexmark/Jansi renderer. */
    public MarkdownMessageConverter() {
        this.configuredRenderer = null;
        this.configuredMarkedOnly = false;
        this.renderer = new FlexmarkAnsiRenderer();
    }

    /**
     * Creates a converter backed by a custom renderer.
     *
     * <p>The {@code plain}, {@code ansi}, and {@code tableWidth} pattern options
     * apply only to the default renderer; a supplied renderer owns its own
     * output policy.
     *
     * @param renderer custom Markdown renderer
     */
    public MarkdownMessageConverter(MarkdownRenderer renderer) {
        this(renderer, false);
    }

    /**
     * Creates a converter backed by a custom renderer with an explicit marker
     * policy. This constructor is intended for programmatic layout integration;
     * XML patterns should use {@code %mdMsg} or {@code %mdMsg{marked}}.
     *
     * @param renderer custom Markdown renderer
     * @param markedOnly whether only {@link MarkdownMarkers#MARKER_NAME} events
     *                   should be rendered
     */
    public MarkdownMessageConverter(MarkdownRenderer renderer, boolean markedOnly) {
        this.configuredRenderer = renderer;
        this.configuredMarkedOnly = markedOnly;
        this.renderer = renderer;
        this.markedOnly = markedOnly;
    }

    @Override
    public void start() {
        List<String> options = getOptionList();
        if (options == null) {
            options = List.of();
        }
        markedOnly = configuredMarkedOnly || ConverterOptions.hasFlag(options, MARKED_OPTION);
        configureDefaultRenderer(options);
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        String rawMessage = event.getFormattedMessage();
        if (rawMessage == null) {
            return "";
        }
        if (rawMessage.isBlank()) {
            return rawMessage;
        }
        if (markedOnly && !MarkdownMarkers.contains(event.getMarkerList())) {
            return rawMessage;
        }
        try {
            return preserveOuterLineBreaks(rawMessage, pad(renderer.render(rawMessage)));
        }
        catch (RuntimeException | StackOverflowError exception) {
            addWarn("Could not render Markdown log message; emitting the original message", exception);
            return rawMessage;
        }
    }

    private void configureDefaultRenderer(List<String> options) {
        if (configuredRenderer != null) {
            return;
        }
        boolean plain = ConverterOptions.hasFlag(options, PLAIN_OPTION);
        boolean ansi = ConverterOptions.hasFlag(options, ANSI_OPTION);
        if (plain && ansi) {
            addWarn("Both 'plain' and 'ansi' were configured for %mdMsg; using plain output");
        }
        renderer = new FlexmarkAnsiRenderer(
                ansiEnabled(plain, ansi),
                new MarkdownRendererOptions(tableWidth(options))
                        .withTableIndent(LEFT_PADDING.length()),
                ConverterOptions.palette(options, "%mdMsg", this::addWarn, this::addWarn));
    }

    private int tableWidth(List<String> options) {
        List<String> configured = ConverterOptions.named(options, TABLE_WIDTH_OPTION);
        if (configured.isEmpty()) {
            return MarkdownRendererOptions.DEFAULT_TABLE_WIDTH;
        }
        if (configured.size() > 1) {
            addWarn("Multiple tableWidth options were configured for %mdMsg; using the last one");
        }
        String option = configured.getLast();
        int separator = option.indexOf('=');
        if (separator < 0 || option.substring(separator + 1).strip().isEmpty()) {
            return invalidTableWidth(option);
        }
        try {
            int width = Integer.parseInt(option.substring(separator + 1).strip());
            if (!MarkdownRendererOptions.isValidTableWidth(width)) {
                return invalidTableWidth(option);
            }
            return width;
        }
        catch (NumberFormatException exception) {
            return invalidTableWidth(option);
        }
    }

    private int invalidTableWidth(String option) {
        addWarn("Invalid %mdMsg option '" + option + "'; using the default tableWidth="
                + MarkdownRendererOptions.DEFAULT_TABLE_WIDTH);
        return MarkdownRendererOptions.DEFAULT_TABLE_WIDTH;
    }

    private static boolean ansiEnabled(boolean plain, boolean ansi) {
        if (plain) {
            return false;
        }
        return ansi || AnsiSupport.isAutoEnabled();
    }

    private static String preserveOuterLineBreaks(String source, String rendered) {
        String lineSeparator = System.lineSeparator();
        return lineSeparator.repeat(leadingLineBreaks(source))
                + rendered
                + lineSeparator.repeat(trailingLineBreaks(source));
    }

    private static int leadingLineBreaks(String source) {
        int count = 0;
        int index = 0;
        while (index < source.length()) {
            char character = source.charAt(index);
            if (character == '\n') {
                count++;
                index++;
            }
            else if (character == '\r') {
                count++;
                index += index + 1 < source.length() && source.charAt(index + 1) == '\n'
                        ? 2
                        : 1;
            }
            else {
                break;
            }
        }
        return count;
    }

    private static int trailingLineBreaks(String source) {
        int count = 0;
        int index = source.length() - 1;
        while (index >= 0) {
            char character = source.charAt(index);
            if (character == '\n') {
                count++;
                index -= index > 0 && source.charAt(index - 1) == '\r' ? 2 : 1;
            }
            else if (character == '\r') {
                count++;
                index--;
            }
            else {
                break;
            }
        }
        return count;
    }

    private static String pad(String rendered) {
        return NON_EMPTY_LINE_START.matcher(rendered).replaceAll(LEFT_PADDING);
    }
}
