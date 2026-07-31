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

import java.util.Objects;

import lombok.val;

/** Receives lifecycle events from an {@link LlmStepRunner}. */
public interface LlmStepRunnerCallback {

    /** Silent callback for standalone runners without lifecycle observers. */
    LlmStepRunnerCallback NO_OP = new LlmStepRunnerCallback() {
        @Override public void stepStarted(StepRequest request) { }
        @Override public void responseUnparseable(StepRequest request, Throwable cause) { }
        @Override public void stepCompleted(StepRequest request, StepRun run) { }
    };

    /** Invoked before the actor model starts a step. */
    void stepStarted(StepRequest request);

    /** Invoked when the actor response cannot be converted to a structured verdict. */
    void responseUnparseable(StepRequest request, Throwable cause);

    /** Invoked after the actor step completes. */
    void stepCompleted(StepRequest request, StepRun run);

    /**
     * Returns a callback that invokes this callback followed by {@code after} for each
     * lifecycle event. If this callback throws, {@code after} is not invoked.
     */
    default LlmStepRunnerCallback andThen(LlmStepRunnerCallback after) {
        Objects.requireNonNull(after, "after");
        val before = this;
        return new LlmStepRunnerCallback() {
            @Override
            public void stepStarted(StepRequest request) {
                before.stepStarted(request);
                after.stepStarted(request);
            }

            @Override
            public void responseUnparseable(StepRequest request, Throwable cause) {
                before.responseUnparseable(request, cause);
                after.responseUnparseable(request, cause);
            }

            @Override
            public void stepCompleted(StepRequest request, StepRun run) {
                before.stepCompleted(request, run);
                after.stepCompleted(request, run);
            }
        };
    }
}
