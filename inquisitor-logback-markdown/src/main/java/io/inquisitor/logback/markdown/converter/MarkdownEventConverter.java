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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import io.inquisitor.logback.markdown.ansi.AnsiStyler;
import io.inquisitor.logback.markdown.ansi.AnsiSupport;
import io.inquisitor.logback.markdown.marker.MarkdownMarkers;
import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPalettes;
import io.inquisitor.logback.markdown.renderer.FlexmarkAnsiRenderer;
import io.inquisitor.logback.markdown.renderer.MarkdownRenderer;
import org.jspecify.annotations.Nullable;

/**
 * Renders a complete marker-selected console event: a compact Powerline
 * time/level header followed by an indented Markdown message body, with blank
 * lines separating complete events.
 *
 * <p>Unmarked events produce no output, which lets a layout containing only
 * {@code %mdEvent} act as an exclusive Markdown console without affecting file
 * or structured appenders.
 */
public final class MarkdownEventConverter extends ThrowableHandlingConverter {

    private static final String LEFT_CAP = "";
    private static final String SEPARATOR = "";
    private static final String RIGHT_CAP = "";
    private static final String PLAIN_OPTION = "plain";
    private static final String ANSI_OPTION = "ansi";
    private static final String PALETTE_OPTION = "palette";
    private static final String CONTENT_INDENT = "    ";
    private static final Pattern NON_EMPTY_LINE_START = Pattern.compile("(?m)^(?=.)");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final @Nullable MarkdownRenderer configuredRenderer;
    private final @Nullable Boolean configuredAnsiEnabled;
    private final @Nullable MarkdownPalette configuredPalette;
    private volatile MarkdownRenderer renderer;
    private volatile AnsiStyler styler;

    /** Creates a converter backed by the default renderer and ANSI detection. */
    public MarkdownEventConverter() {
        this.configuredRenderer = null;
        this.configuredAnsiEnabled = null;
        this.configuredPalette = null;
        boolean ansiEnabled = AnsiSupport.isAutoEnabled();
        this.renderer = new FlexmarkAnsiRenderer(ansiEnabled, MarkdownPalettes.defaultPalette());
        this.styler = new AnsiStyler(ansiEnabled, MarkdownPalettes.defaultPalette());
    }

    /**
     * Creates a converter backed by a custom renderer and automatic header styling.
     *
     * @param renderer Markdown body renderer
     */
    public MarkdownEventConverter(MarkdownRenderer renderer) {
        this(renderer, MarkdownPalettes.defaultPalette());
    }

    /**
     * Creates a converter backed by a custom renderer, automatic header styling,
     * and an explicit palette.
     *
     * @param renderer Markdown body renderer
     * @param palette terminal color palette for the event header
     */
    public MarkdownEventConverter(MarkdownRenderer renderer, MarkdownPalette palette) {
        this.configuredRenderer = renderer;
        this.configuredAnsiEnabled = null;
        this.configuredPalette = palette;
        this.renderer = renderer;
        this.styler = new AnsiStyler(AnsiSupport.isAutoEnabled(), palette);
    }

    /**
     * Creates a converter backed by a custom renderer and explicit header styling.
     *
     * @param renderer Markdown body renderer
     * @param ansiEnabled whether the Powerline event header should use ANSI colors
     */
    public MarkdownEventConverter(MarkdownRenderer renderer, boolean ansiEnabled) {
        this(renderer, ansiEnabled, MarkdownPalettes.defaultPalette());
    }

    /**
     * Creates a converter backed by a custom renderer, header policy, and palette.
     *
     * @param renderer Markdown body renderer
     * @param ansiEnabled whether the Powerline event header should use ANSI colors
     * @param palette terminal color palette for the event header
     */
    public MarkdownEventConverter(
            MarkdownRenderer renderer,
            boolean ansiEnabled,
            MarkdownPalette palette) {
        this.configuredRenderer = renderer;
        this.configuredAnsiEnabled = ansiEnabled;
        this.configuredPalette = palette;
        this.renderer = renderer;
        this.styler = new AnsiStyler(ansiEnabled, palette);
    }

    @Override
    public void start() {
        List<String> options = getOptionList();
        if (options == null) {
            options = List.of();
        }
        boolean plain = hasOption(options, PLAIN_OPTION);
        boolean ansi = hasOption(options, ANSI_OPTION);
        if (plain && ansi) {
            addWarn("Both 'plain' and 'ansi' were configured for %mdEvent; using plain output");
        }
        boolean ansiEnabled = configuredAnsiEnabled != null
                ? configuredAnsiEnabled
                : plain ? false : ansi || AnsiSupport.isAutoEnabled();
        MarkdownPalette palette = configuredPalette == null
                ? palette(options)
                : configuredPalette;
        if (configuredRenderer == null) {
            renderer = new FlexmarkAnsiRenderer(ansiEnabled, palette);
        }
        styler = new AnsiStyler(ansiEnabled, palette);
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        if (!MarkdownMarkers.contains(event.getMarkerList())) {
            return "";
        }
        String rawMessage = event.getFormattedMessage();
        String body = rawMessage == null ? "" : render(rawMessage);
        String lineSeparator = System.lineSeparator();
        StringBuilder result = new StringBuilder(lineSeparator)
                .append(header(event))
                .append(lineSeparator)
                .append(lineSeparator);
        if (!body.isBlank()) {
            result.append(indent(body.stripTrailing())).append(lineSeparator);
        }
        if (event.getThrowableProxy() != null) {
            result.append(indent(ThrowableProxyUtil.asString(event.getThrowableProxy()).stripTrailing()))
                    .append(lineSeparator);
        }
        return result.toString();
    }

    private String render(String rawMessage) {
        if (rawMessage.isBlank()) {
            return "";
        }
        String normalized = rawMessage.strip();
        try {
            return renderer.render(normalized);
        }
        catch (RuntimeException | StackOverflowError exception) {
            addWarn("Could not render Markdown log event; emitting the original message", exception);
            return normalized;
        }
    }

    private String header(ILoggingEvent event) {
        String time = TIME_FORMAT.format(Instant.ofEpochMilli(event.getTimeStamp()));
        String level = event.getLevel().toString();
        AnsiStyler.PowerlineStyle timeStyle = AnsiStyler.PowerlineStyle.TIMESTAMP;
        AnsiStyler.PowerlineStyle levelStyle = levelStyle(event.getLevel());
        return styler.powerlineCap(timeStyle, LEFT_CAP)
                + styler.open(timeStyle) + time + styler.reset()
                + styler.powerlineTransition(timeStyle, levelStyle, SEPARATOR)
                + styler.open(levelStyle) + level + styler.reset()
                + styler.powerlineCap(levelStyle, RIGHT_CAP);
    }

    private static AnsiStyler.PowerlineStyle levelStyle(Level level) {
        return switch (level.toInt()) {
            case Level.ERROR_INT -> AnsiStyler.PowerlineStyle.FAILURE;
            case Level.WARN_INT -> AnsiStyler.PowerlineStyle.WARNING;
            case Level.INFO_INT -> AnsiStyler.PowerlineStyle.STATUS;
            case Level.DEBUG_INT -> AnsiStyler.PowerlineStyle.DEBUG;
            case Level.TRACE_INT -> AnsiStyler.PowerlineStyle.TRACE;
            default -> AnsiStyler.PowerlineStyle.DURATION;
        };
    }

    private static boolean hasOption(List<String> options, String expected) {
        return options.stream().map(String::strip).anyMatch(expected::equalsIgnoreCase);
    }

    private MarkdownPalette palette(List<String> options) {
        List<String> configured = options.stream()
                .map(String::strip)
                .filter(option -> option.regionMatches(
                        true, 0, PALETTE_OPTION + "=", 0, PALETTE_OPTION.length() + 1))
                .toList();
        if (configured.isEmpty()) {
            return MarkdownPalettes.defaultPalette();
        }
        if (configured.size() > 1) {
            addWarn("Multiple palette options were configured for %mdEvent; using the last one");
        }
        String option = configured.getLast();
        String name = option.substring(option.indexOf('=') + 1).strip();
        try {
            return MarkdownPalettes.find(name).orElseGet(() -> invalidPalette(option));
        }
        catch (RuntimeException | ServiceConfigurationError exception) {
            addWarn("Could not resolve %mdEvent option '" + option
                    + "'; using palette=gruvbox", exception);
            return MarkdownPalettes.defaultPalette();
        }
    }

    private MarkdownPalette invalidPalette(String option) {
        addWarn("Invalid %mdEvent option '" + option + "'; using palette=gruvbox");
        return MarkdownPalettes.defaultPalette();
    }

    private static String indent(String value) {
        return NON_EMPTY_LINE_START.matcher(value).replaceAll(CONTENT_INDENT);
    }

}
