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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.inquisitor.logback.markdown.ansi.AnsiStyler;
import io.inquisitor.logback.markdown.marker.MarkdownMarkers;
import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPalettes;
import io.inquisitor.logback.markdown.renderer.FlexmarkAnsiRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarkdownEventConverterTest {

    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");

    @Test
    void separatesEventsAndIndentsTheMarkdownBody() {
        MarkdownEventConverter converter = converter(false);

        String rendered = converter.convert(marked(Level.INFO, "\n\n## Fetch account\n"));

        assertTrue(rendered.matches(
                "\\R\\d{2}:\\d{2}:\\d{2}\\.\\d{3}INFO\\R"
                        + "\\R    Fetch account\\R"));
        assertFalse(rendered.contains("    "));
    }

    @Test
    void suppressesUnmarkedEventsCompletely() {
        MarkdownEventConverter converter = converter(false);

        assertEquals("", converter.convert(event(Level.INFO, "ordinary")));
    }

    @Test
    void indentsEveryNonemptyContentLine() {
        MarkdownEventConverter converter = new MarkdownEventConverter(
                markdown -> "first\n\nsecond", false);
        converter.start();

        String rendered = converter.convert(marked(Level.INFO, "ignored"));

        assertTrue(rendered.endsWith("INFO" + System.lineSeparator()
                + System.lineSeparator()
                + "    first" + System.lineSeparator()
                + System.lineSeparator()
                + "    second" + System.lineSeparator()));
    }

    @ParameterizedTest
    @CsvSource({
            "INFO, '48;2;152;151;26'",
            "WARN, '48;2;215;153;33'",
            "ERROR, '48;2;204;36;29'",
            "DEBUG, '48;2;177;98;134'",
            "TRACE, '48;2;102;92;84'"
    })
    void colorsEveryLogLevelSemantically(String levelName, String backgroundCode) {
        MarkdownEventConverter converter = converter(true);
        Level level = Level.toLevel(levelName);

        String rendered = converter.convert(marked(level, "message"));

        assertTrue(rendered.contains(backgroundCode));
        assertTrue(stripAnsi(rendered).matches(
                "\\R\\d{2}:\\d{2}:\\d{2}\\.\\d{3}" + levelName
                        + "\\R\\R    message\\R"));
    }

    @Test
    void usesTheNeutralGruvboxTimestampPalette() {
        MarkdownEventConverter converter = converter(true);

        String rendered = converter.convert(marked(Level.INFO, "message"));

        assertTrue(rendered.contains("48;2;60;56;54"));
        assertTrue(rendered.contains("38;2;251;241;199"));
    }

    @Test
    void appliesTheSelectedPaletteToHeaderAndMarkdownBody() {
        MarkdownPalette palette = MarkdownPalettes.require("catppuccin");
        MarkdownEventConverter converter = new MarkdownEventConverter(
                new FlexmarkAnsiRenderer(true, palette),
                true,
                palette);
        converter.start();

        String rendered = converter.convert(marked(Level.INFO, "## message"));

        assertTrue(rendered.contains("48;2;49;50;68"));
        assertTrue(rendered.contains("48;2;166;227;161"));
        assertTrue(new AnsiStyler(true, palette)
                .open(AnsiStyler.PowerlineStyle.STATUS)
                .contains("38;2;49;50;68"));
        assertTrue(rendered.contains("38;2;148;226;213"));
    }

    @Test
    void palettePatternOptionStylesTheDefaultEventRenderer() {
        MarkdownEventConverter converter = new MarkdownEventConverter();
        converter.setOptionList(List.of("ansi", "palette=tokyo-night"));
        converter.start();

        String rendered = converter.convert(marked(Level.DEBUG, "## message"));

        assertTrue(rendered.contains("48;2;36;40;59"));
        assertTrue(rendered.contains("48;2;187;154;247"));
        assertTrue(rendered.contains("38;2;125;207;255"));
    }

    @Test
    void fallsBackToRawBodyWhenRenderingFails() {
        MarkdownEventConverter converter = new MarkdownEventConverter(markdown -> {
            throw new IllegalStateException("renderer failed");
        }, false);
        converter.start();

        String rendered = converter.convert(marked(Level.WARN, "**raw**"));

        assertTrue(rendered.endsWith("WARN" + System.lineSeparator()
                + System.lineSeparator()
                + "    **raw**" + System.lineSeparator()));
    }

    @Test
    void retainsTheThrowableForMarkedEvents() {
        MarkdownEventConverter converter = converter(false);
        LoggingEvent event = marked(Level.ERROR, "request failed");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("boom")));

        String rendered = converter.convert(event);

        assertTrue(rendered.contains("ERROR"));
        assertTrue(rendered.contains("    java.lang.IllegalStateException: boom"));
    }

    private static MarkdownEventConverter converter(boolean ansiEnabled) {
        MarkdownEventConverter converter = new MarkdownEventConverter(
                new FlexmarkAnsiRenderer(ansiEnabled), ansiEnabled);
        converter.start();
        return converter;
    }

    private static LoggingEvent marked(Level level, String message) {
        LoggingEvent event = event(level, message);
        event.addMarker(MarkdownMarkers.markdown());
        return event;
    }

    private static LoggingEvent event(Level level, String message) {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(level);
        event.setMessage(message);
        return event;
    }

    private static String stripAnsi(String value) {
        return ANSI.matcher(value).replaceAll("");
    }
}
