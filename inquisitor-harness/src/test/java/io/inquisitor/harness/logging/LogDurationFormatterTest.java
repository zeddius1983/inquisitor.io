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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class LogDurationFormatterTest {

    @Test
    void selectsHumanScaleAndKeepsUsefulPrecision() {
        assertThat(LogDurationFormatter.format(Duration.ofMillis(842))).isEqualTo("842 ms");
        assertThat(LogDurationFormatter.format(Duration.ofMillis(13_289))).isEqualTo("13.289 s");
        assertThat(LogDurationFormatter.format(Duration.ofSeconds(125).plusMillis(400)))
                .isEqualTo("2 min 5.4 s");
        assertThat(LogDurationFormatter.format(
                Duration.ofHours(1).plusMinutes(2).plusSeconds(3).plusMillis(4)))
                .isEqualTo("1 h 2 min 3.004 s");
    }

    @Test
    void omitsEmptyLowerUnits() {
        assertThat(LogDurationFormatter.format(Duration.ofSeconds(1))).isEqualTo("1 s");
        assertThat(LogDurationFormatter.format(Duration.ofMinutes(1))).isEqualTo("1 min");
        assertThat(LogDurationFormatter.format(Duration.ofHours(1))).isEqualTo("1 h");
    }
}
