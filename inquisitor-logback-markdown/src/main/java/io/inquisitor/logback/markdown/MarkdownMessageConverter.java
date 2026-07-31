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

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;

/**
 * Logback pattern converter for {@code %mdMsg}, {@code %mdMsg{marked}}, and the
 * optional {@code plain}/{@code ansi} output policies.
 */
public final class MarkdownMessageConverter extends MessageConverter {

    private static final String MARKED_OPTION = "marked";
    private static final String PLAIN_OPTION = "plain";
    private static final String ANSI_OPTION = "ansi";

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
     * <p>The {@code plain} and {@code ansi} pattern options apply only to the
     * default renderer; a supplied renderer owns its own output policy.
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
        markedOnly = configuredMarkedOnly || hasOption(options, MARKED_OPTION);
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
        if (markedOnly && !isMarkdown(event.getMarkerList())) {
            return rawMessage;
        }
        try {
            return renderer.render(rawMessage);
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
        boolean plain = hasOption(options, PLAIN_OPTION);
        boolean ansi = hasOption(options, ANSI_OPTION);
        if (plain && ansi) {
            addWarn("Both 'plain' and 'ansi' were configured for %mdMsg; using plain output");
        }
        renderer = new FlexmarkAnsiRenderer(ansiEnabled(plain, ansi));
    }

    private static boolean hasOption(List<String> options, String expected) {
        return options.stream().map(String::strip).anyMatch(expected::equalsIgnoreCase);
    }

    private static boolean ansiEnabled(boolean plain, boolean ansi) {
        if (plain) {
            return false;
        }
        return ansi || AnsiSupport.isAutoEnabled();
    }

    private static boolean isMarkdown(@Nullable List<Marker> markers) {
        return markers != null && markers.stream()
                .anyMatch(marker -> marker.contains(MarkdownMarkers.MARKER_NAME));
    }
}
