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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.inquisitor.logback.markdown.marker.MarkdownMarkers;
import io.inquisitor.logback.markdown.renderer.FlexmarkAnsiRenderer;
import io.inquisitor.logback.markdown.renderer.MarkdownRenderer;
import io.inquisitor.logback.markdown.renderer.MarkdownRendererOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

class MarkdownMessageConverterTest {

    @Test
    void defaultModeRendersEveryMessage() {
        MarkdownMessageConverter converter = converter(new FlexmarkAnsiRenderer(MarkdownRendererOptions.plain()));

        assertEquals("    rendered", converter.convert(event("**rendered**")));
    }

    @Test
    void markedModeLeavesOrdinaryMessagesByteForByteUnchanged() {
        MarkdownMessageConverter converter = markedConverter(new FlexmarkAnsiRenderer(MarkdownRendererOptions.plain()));
        String raw = "## ordinary **message**";

        assertEquals(raw, converter.convert(event(raw)));
    }

    @Test
    void markedModeRendersDirectAndNestedMarkdownMarkers() {
        MarkdownMessageConverter converter = markedConverter(new FlexmarkAnsiRenderer(MarkdownRendererOptions.plain()));
        LoggingEvent direct = event("## direct");
        direct.addMarker(MarkdownMarkers.markdown());

        Marker parent = MarkerFactory.getDetachedMarker("PARENT");
        parent.add(MarkdownMarkers.markdown());
        LoggingEvent nested = event("## nested");
        nested.addMarker(parent);

        assertEquals("    direct", converter.convert(direct));
        assertEquals("    nested", converter.convert(nested));
    }

    @Test
    void markedAndAnsiOptionsAreOrderIndependent() {
        MarkdownMessageConverter converter = defaultConverter(
                "tableWidth=100", "ansi", "marked");
        LoggingEvent marked = event("## rendered");
        marked.addMarker(MarkdownMarkers.markdown());
        String ordinary = "## ordinary";

        assertTrue(converter.convert(marked).contains("\u001B["));
        assertEquals(ordinary, converter.convert(event(ordinary)));
    }

    @Test
    void paletteOptionStylesTheDefaultRenderer() {
        MarkdownMessageConverter converter = defaultConverter("ansi", "palette=nord");

        String rendered = converter.convert(event("## rendered"));

        assertTrue(rendered.contains("38;2;143;188;187"));
        assertFalse(rendered.contains("38;2;104;157;106"));
    }

    @Test
    void paletteOptionResolvesAServiceProvidedPalette() {
        MarkdownMessageConverter converter = defaultConverter(
                "ansi", "palette=solarized-test");

        String rendered = converter.convert(event("## rendered"));

        assertTrue(rendered.contains("38;2;42;161;152"));
    }

    @Test
    void invalidPaletteFallsBackToGruvboxAndReportsAStatusWarning() {
        LoggerContext context = new LoggerContext();
        MarkdownMessageConverter converter = new MarkdownMessageConverter();
        converter.setContext(context);
        converter.setOptionList(List.of("ansi", "palette=unknown"));

        converter.start();

        assertTrue(converter.convert(event("## rendered"))
                .contains("38;2;104;157;106"));
        assertTrue(context.getStatusManager().getCopyOfStatusList().stream()
                .anyMatch(status -> status.getMessage().contains("using palette=gruvbox")));
    }

    @Test
    void tableWidthOptionWrapsTablesWithinTheConfiguredMessageBodyWidth() {
        MarkdownMessageConverter converter = defaultConverter("plain", "tableWidth=24");
        String rendered = converter.convert(event("""
                | Key | Value |
                |---|---|
                | notes | alpha beta gamma delta |
                """));

        assertTrue(rendered.lines().allMatch(line -> line.length() <= 24));
        assertTrue(rendered.contains("alpha"));
        assertTrue(rendered.contains("beta"));
        assertTrue(rendered.contains("gamma"));
        assertTrue(rendered.contains("delta"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tableWidth", "tableWidth=", "tableWidth=nope",
            "tableWidth=19", "tableWidth=1001"})
    void malformedTableWidthFallsBackAndReportsAStatusWarning(String option) {
        LoggerContext context = new LoggerContext();
        MarkdownMessageConverter converter = new MarkdownMessageConverter();
        converter.setContext(context);
        converter.setOptionList(List.of("plain", option));

        converter.start();

        assertTrue(context.getStatusManager().getCopyOfStatusList().stream()
                .anyMatch(status -> status.getMessage().contains("default tableWidth=120")));
    }

    @Test
    void plainOptionDisablesAnsiForTheDefaultRenderer() {
        MarkdownMessageConverter converter = defaultConverter("marked", "plain");
        LoggingEvent marked = event("## rendered");
        marked.addMarker(MarkdownMarkers.markdown());

        String rendered = converter.convert(marked);

        assertEquals("    rendered", rendered);
        assertFalse(rendered.contains("\u001B["));
    }

    @Test
    void programmaticMarkedModeDoesNotDependOnPatternOptions() {
        MarkdownMessageConverter converter = new MarkdownMessageConverter(
                new FlexmarkAnsiRenderer(MarkdownRendererOptions.plain()), true);
        converter.start();
        LoggingEvent marked = event("## rendered");
        marked.addMarker(MarkdownMarkers.markdown());
        String ordinary = "## ordinary";

        assertEquals("    rendered", converter.convert(marked));
        assertEquals(ordinary, converter.convert(event(ordinary)));
    }

    @Test
    void preservesBlankMessagesAndNormalizesNullToEmpty() {
        MarkdownMessageConverter converter = converter(new FlexmarkAnsiRenderer(MarkdownRendererOptions.plain()));
        LoggingEvent nullMessage = new LoggingEvent();

        assertEquals("", converter.convert(nullMessage));
        assertEquals("", converter.convert(event("")));
        assertEquals("  \t", converter.convert(event("  \t")));
    }

    @Test
    void preservesRequestedOuterLineBreaksAroundRenderedBlocks() {
        MarkdownMessageConverter converter = markedConverter(new FlexmarkAnsiRenderer(MarkdownRendererOptions.plain()));
        LoggingEvent block = event("\n\n## rendered\r\n");
        block.addMarker(MarkdownMarkers.markdown());

        assertEquals(System.lineSeparator().repeat(2)
                        + "    rendered" + System.lineSeparator(),
                converter.convert(block));
    }

    @Test
    void padsEveryNonEmptyRenderedLineWithoutAddingWhitespaceToBlankLines() {
        MarkdownMessageConverter converter = converter(ignored -> "first\n\n  already indented");

        assertEquals("    first\n\n      already indented", converter.convert(event("markdown")));
    }

    @Test
    void fallsBackToRawMessageWhenRenderingFails() {
        MarkdownMessageConverter converter = converter(markdown -> {
            throw new IllegalStateException("renderer failed");
        });

        assertEquals("**raw**", converter.convert(event("**raw**")));
    }

    @Test
    void fallsBackToRawMessageWhenRenderingOverflowsTheStack() {
        MarkdownMessageConverter converter = converter(markdown -> {
            throw new StackOverflowError("renderer recursed too deeply");
        });

        assertEquals("**raw**", converter.convert(event("**raw**")));
    }

    @Test
    void markerHelperUsesDocumentedName() {
        assertEquals(MarkdownMarkers.MARKER_NAME, MarkdownMarkers.markdown().getName());
    }

    private static MarkdownMessageConverter markedConverter(MarkdownRenderer renderer) {
        MarkdownMessageConverter converter = new MarkdownMessageConverter(renderer);
        converter.setOptionList(List.of("marked"));
        converter.start();
        return converter;
    }

    private static MarkdownMessageConverter converter(MarkdownRenderer renderer) {
        MarkdownMessageConverter converter = new MarkdownMessageConverter(renderer);
        converter.start();
        return converter;
    }

    private static MarkdownMessageConverter defaultConverter(String... options) {
        MarkdownMessageConverter converter = new MarkdownMessageConverter();
        converter.setOptionList(List.of(options));
        converter.start();
        return converter;
    }

    private static LoggingEvent event(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        return event;
    }
}
