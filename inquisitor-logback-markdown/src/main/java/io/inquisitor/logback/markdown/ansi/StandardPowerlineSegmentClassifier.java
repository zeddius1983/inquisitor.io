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

import static io.inquisitor.logback.markdown.ansi.AnsiStyler.PowerlineStyle.DURATION;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.PowerlineStyle.FAILURE;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.PowerlineStyle.STATUS;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.PowerlineStyle.WARNING;

import java.util.List;
import java.util.Locale;

import io.inquisitor.logback.markdown.ansi.AnsiStyler.PowerlineStyle;
import org.jspecify.annotations.Nullable;

/** Default classifier containing only general terminal-log concepts. */
final class StandardPowerlineSegmentClassifier implements PowerlineSegmentClassifier {

    static final StandardPowerlineSegmentClassifier INSTANCE =
            new StandardPowerlineSegmentClassifier();

    private StandardPowerlineSegmentClassifier() {
    }

    @Override
    public PowerlineStyle classify(int index, List<String> segments) {
        String normalized = segments.get(index).strip().toUpperCase(Locale.ROOT);
        @Nullable PowerlineStyle semantic = switch (normalized) {
            case "FAIL", "FAILED", "ERROR", "ABORTED", "UNSUPPORTED" -> FAILURE;
            case "SKIP", "SKIPPED", "WARN", "WARNING" -> WARNING;
            case "PASS", "PASSED", "SUCCESS", "RUN", "RUNNING", "EXECUTE",
                    "COMPLETED" -> STATUS;
            default -> httpStatus(normalized);
        };
        if (semantic != null) {
            return semantic;
        }
        if (normalized.startsWith("⧖ ") || normalized.startsWith("/")) {
            return DURATION;
        }
        return PowerlineStyle.at(index);
    }

    private static @Nullable PowerlineStyle httpStatus(String text) {
        if (!text.startsWith("HTTP ")) {
            return null;
        }
        String statusText = text.substring("HTTP ".length()).strip();
        if (statusText.length() != 3 || !statusText.chars().allMatch(Character::isDigit)) {
            return null;
        }
        int status = Integer.parseInt(statusText);
        if (status >= 500) {
            return FAILURE;
        }
        if (status >= 400) {
            return WARNING;
        }
        return status >= 200 ? STATUS : null;
    }
}
