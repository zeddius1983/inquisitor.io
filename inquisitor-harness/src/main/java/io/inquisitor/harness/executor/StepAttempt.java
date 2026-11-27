/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.inquisitor.harness.executor;

import java.time.Duration;
import java.util.List;
import io.inquisitor.harness.model.StepVerdict;
import io.inquisitor.harness.model.ToolCallRecord;
import org.jspecify.annotations.Nullable;

/** One actor turn, including only the tool calls added during this attempt. */
public record StepAttempt(int number, String rawResponse, @Nullable StepVerdict verdict,
        @Nullable String error, List<ToolCallRecord> toolCalls, Duration elapsed) {
    public StepAttempt {
        toolCalls = List.copyOf(toolCalls);
    }
}
