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

package io.inquisitor.logback.markdown.ansi;

import org.jline.jansi.Ansi;
import org.jspecify.annotations.Nullable;

/** Detects whether ANSI output is appropriate for the current process. */
public final class AnsiSupport {

    private static final String NO_COLOR = "NO_COLOR";
    private static final String TERM = "TERM";

    private AnsiSupport() {
    }

    /** Returns whether the current console and environment support ANSI styling. */
    public static boolean isAutoEnabled() {
        try {
            return isAutoEnabled(
                    System.getenv(NO_COLOR),
                    System.getenv(TERM),
                    System.console() != null,
                    Ansi.isEnabled());
        }
        catch (SecurityException ignored) {
            return false;
        }
    }

    static boolean isAutoEnabled(
            @Nullable String noColor,
            @Nullable String term,
            boolean consolePresent,
            boolean jansiEnabled) {
        return jansiEnabled
                && consolePresent
                && noColor == null
                && (term == null || !"dumb".equalsIgnoreCase(term.strip()));
    }
}
