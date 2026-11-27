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

package io.inquisitor.harness.executor;

import java.time.Duration;
import java.util.List;

import io.inquisitor.harness.model.StepVerdict;
import io.inquisitor.harness.model.ToolCallRecord;

/**
 * The outcome of running one step: the model's {@link StepVerdict}, the trace of tool
 * calls it made while producing that verdict, and its elapsed time.
 *
 * <p>Surfacing the trace through the return value lets a decorating {@link StepRunner}
 * read it directly — with no thread-locals or shared state — to score how well-grounded the
 * verdict is. The trace is empty unless tool-call capture is wired (only the
 * evaluation context wraps the tools to record).
 *
 * @param verdict   the LLM's structured judgement for the step
 * @param toolCalls the tool invocations made during the step, in call order
 * @param elapsed total wall-clock time spent establishing the step result
 * @param actorElapsed wall-clock time spent in actor calls; excludes reviewer calls
 * @param synthetic whether the verdict was fabricated by the harness (an empty,
 *                  unparseable or missing model response degraded to a FAIL) rather than
 *                  claimed by the actor. A synthetic verdict is a run-health fact, not an
 *                  actor claim — evaluation skips judging it
 * @param attempts immutable actor-turn history, empty for legacy/custom runners
 */
public record StepRun(StepVerdict verdict, List<ToolCallRecord> toolCalls, Duration elapsed,
                      Duration actorElapsed, boolean synthetic, List<StepAttempt> attempts) {

    public StepRun {
        toolCalls = List.copyOf(toolCalls);
        attempts = List.copyOf(attempts);
    }

    public StepRun(StepVerdict verdict, List<ToolCallRecord> toolCalls, Duration elapsed,
            boolean synthetic, List<StepAttempt> attempts) {
        this(verdict, toolCalls, elapsed, elapsed, synthetic, attempts);
    }

    public StepRun(StepVerdict verdict, List<ToolCallRecord> toolCalls, Duration elapsed, boolean synthetic) {
        this(verdict, toolCalls, elapsed, elapsed, synthetic, List.of());
    }

    /** A run whose verdict the actor genuinely produced. */
    public StepRun(StepVerdict verdict, List<ToolCallRecord> toolCalls, Duration elapsed) {
        this(verdict, toolCalls, elapsed, false);
    }
}
