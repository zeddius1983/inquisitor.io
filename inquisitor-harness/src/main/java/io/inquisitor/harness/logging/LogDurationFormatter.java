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

package io.inquisitor.harness.logging;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;

import lombok.val;

/** Human-oriented duration formatting for harness log messages. */
public final class LogDurationFormatter {

    private static final long MILLIS_PER_SECOND = 1_000;
    private static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;

    private LogDurationFormatter() { }

    public static String format(Duration duration) {
        val totalMillis = duration.toMillis();
        if (totalMillis < MILLIS_PER_SECOND) {
            return totalMillis + " ms";
        }

        val parts = new ArrayList<String>(3);
        val hours = totalMillis / MILLIS_PER_HOUR;
        val minutes = totalMillis % MILLIS_PER_HOUR / MILLIS_PER_MINUTE;
        val remainingMillis = totalMillis % MILLIS_PER_MINUTE;
        if (hours > 0) {
            parts.add(hours + " h");
        }
        if (minutes > 0) {
            parts.add(minutes + " min");
        }
        if (remainingMillis > 0) {
            parts.add(seconds(remainingMillis));
        }
        return String.join(" ", parts);
    }

    private static String seconds(long millis) {
        return BigDecimal.valueOf(millis, 3).stripTrailingZeros().toPlainString() + " s";
    }
}
