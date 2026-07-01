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

package io.inquisitor.harness.model;

import java.time.Duration;

/**
 * A typed record of one tool invocation made while a step was evaluated — the
 * out-of-band ground truth used to judge whether the model's verdict is earned
 * (it really called the tools it claims, and the results match what they returned).
 *
 * <p>Captured structurally rather than by parsing log lines, so it stays stable for
 * the evaluator and the report. Rendered to text only at the boundary
 * where it is shown to the judge.
 *
 * @param toolName  the invoked tool's name (e.g. {@code httpRequest}, {@code sqlQuery})
 * @param arguments the raw tool input (typically the JSON argument string)
 * @param result    the tool's returned result, verbatim
 * @param order     0-based position of this call within the step
 * @param elapsed   how long the call took
 */
public record ToolCallRecord(String toolName, String arguments, String result, int order, Duration elapsed) {

    /** A one-line rendering for the evaluator transcript and the report. */
    public String describe() {
        return "#%d %s(%s) -> %s".formatted(order, toolName, arguments, result);
    }
}
