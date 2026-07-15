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

package io.inquisitor.harness.evaluation.report;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import lombok.val;
import org.jspecify.annotations.Nullable;

/** Formatting shared by the report renderers. */
final class ReportFormats {

    private ReportFormats() {
    }

    /** {@code 0.975} → {@code "97.5%"}. */
    static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    /** {@code "1h 5m 0s"} / {@code "5m 12s"} / {@code "42s"}. */
    static String humanDuration(long millis) {
        val duration = Duration.ofMillis(millis);
        val hours = duration.toHours();
        val minutes = duration.toMinutesPart();
        val seconds = duration.toSecondsPart();
        return hours > 0 ? "%dh %dm %ds".formatted(hours, minutes, seconds)
                : minutes > 0 ? "%dm %ds".formatted(minutes, seconds)
                : "%ds".formatted(seconds);
    }

    /** {@code "model @ baseUrl"}, degrading gracefully when parts are unknown. */
    static String endpoint(@Nullable String model, @Nullable String baseUrl) {
        return (model == null ? "(unknown model)" : model) + (baseUrl == null ? "" : " @ " + baseUrl);
    }

    /** Judge evaluation score: mean score over evaluated steps; NaN when none. */
    static double evaluationScore(List<StepEvaluationRecord> steps) {
        return steps.stream()
                .filter(StepEvaluationRecord::evaluated)
                .mapToDouble(StepEvaluationRecord::score)
                .average()
                .orElse(Double.NaN);
    }
}
