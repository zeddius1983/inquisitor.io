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

package io.inquisitor.logback.markdown.marker;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/** Identifies log events whose message contains Markdown. */
public final class MarkdownMarkers {

    /** Marker name recognized by {@code %mdMsg{marked}}. */
    public static final String MARKER_NAME = "INQUISITOR_MARKDOWN";

    private MarkdownMarkers() {
    }

    /**
     * Returns the shared SLF4J marker used for Markdown messages.
     *
     * @return the Markdown marker
     */
    public static Marker markdown() {
        return MarkerFactory.getMarker(MARKER_NAME);
    }

    /** Returns whether one marker contains the Markdown marker name. */
    public static boolean contains(@Nullable Marker marker) {
        return marker != null && marker.contains(MARKER_NAME);
    }

    /** Returns whether a marker collection contains the Markdown marker name. */
    public static boolean contains(@Nullable List<Marker> markers) {
        return markers != null && markers.stream().anyMatch(MarkdownMarkers::contains);
    }
}
