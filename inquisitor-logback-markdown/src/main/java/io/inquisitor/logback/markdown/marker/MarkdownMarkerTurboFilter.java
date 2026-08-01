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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;

/** Force-enables marker-selected Markdown events while leaving other events neutral. */
public final class MarkdownMarkerTurboFilter extends TurboFilter {

    @Override
    public FilterReply decide(
            @Nullable Marker marker,
            Logger logger,
            Level level,
            @Nullable String format,
            Object @Nullable [] parameters,
            @Nullable Throwable throwable) {
        return MarkdownMarkers.contains(marker) ? FilterReply.ACCEPT : FilterReply.NEUTRAL;
    }
}
