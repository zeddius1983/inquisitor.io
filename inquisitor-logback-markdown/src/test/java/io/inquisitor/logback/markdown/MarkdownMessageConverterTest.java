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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

class MarkdownMessageConverterTest {

    @Test
    void defaultModeRendersEveryMessage() {
        MarkdownMessageConverter converter = converter(new FlexmarkAnsiRenderer(false));

        assertEquals("rendered", converter.convert(event("**rendered**")));
    }

    @Test
    void markedModeLeavesOrdinaryMessagesByteForByteUnchanged() {
        MarkdownMessageConverter converter = markedConverter(new FlexmarkAnsiRenderer(false));
        String raw = "## ordinary **message**";

        assertEquals(raw, converter.convert(event(raw)));
    }

    @Test
    void markedModeRendersDirectAndNestedMarkdownMarkers() {
        MarkdownMessageConverter converter = markedConverter(new FlexmarkAnsiRenderer(false));
        LoggingEvent direct = event("## direct");
        direct.addMarker(MarkdownMarkers.markdown());

        Marker parent = MarkerFactory.getDetachedMarker("PARENT");
        parent.add(MarkdownMarkers.markdown());
        LoggingEvent nested = event("## nested");
        nested.addMarker(parent);

        assertEquals("direct", converter.convert(direct));
        assertEquals("nested", converter.convert(nested));
    }

    @Test
    void fallsBackToRawMessageWhenRenderingFails() {
        MarkdownMessageConverter converter = converter(markdown -> {
            throw new IllegalStateException("renderer failed");
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

    private static LoggingEvent event(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        return event;
    }
}
