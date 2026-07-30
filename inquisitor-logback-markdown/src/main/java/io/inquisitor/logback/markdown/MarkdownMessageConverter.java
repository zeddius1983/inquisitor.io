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
 * Logback pattern converter for {@code %mdMsg} and {@code %mdMsg{marked}}.
 */
public final class MarkdownMessageConverter extends MessageConverter {

    private static final String MARKED_OPTION = "marked";

    private final MarkdownRenderer renderer;
    private boolean markedOnly;

    /** Creates a converter backed by the default Flexmark/Jansi renderer. */
    public MarkdownMessageConverter() {
        this(new FlexmarkAnsiRenderer());
    }

    MarkdownMessageConverter(MarkdownRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void start() {
        markedOnly = MARKED_OPTION.equalsIgnoreCase(getFirstOption());
        super.start();
    }

    @Override
    public String convert(ILoggingEvent event) {
        String rawMessage = event.getFormattedMessage();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return rawMessage == null ? "" : rawMessage;
        }
        if (markedOnly && !isMarkdown(event.getMarkerList())) {
            return rawMessage;
        }
        try {
            return renderer.render(rawMessage);
        }
        catch (RuntimeException exception) {
            addWarn("Could not render Markdown log message; emitting the original message", exception);
            return rawMessage;
        }
    }

    private static boolean isMarkdown(@Nullable List<Marker> markers) {
        return markers != null && markers.stream()
                .anyMatch(marker -> marker.contains(MarkdownMarkers.MARKER_NAME));
    }
}
