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

package io.inquisitor.harness.evaluation;

import java.util.Objects;

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import lombok.val;
import org.springframework.ai.evaluation.EvaluationResponse;

/** Receives lifecycle events from an {@link EvaluationStepRunner}. */
public interface EvaluationStepRunnerCallback {

    /** Invoked before the judge starts an evaluation. */
    void evaluationStarted(StepRequest request, StepRun actorRun);

    /** Invoked when a step is deliberately excluded from evaluation. */
    void evaluationSkipped(StepRequest request, StepRun actorRun, String reason);

    /** Invoked when the judge call fails without affecting the actor result. */
    void evaluationFailed(StepRequest request, StepRun actorRun, Throwable cause);

    /** Invoked after the judge produces its category and score. */
    void evaluationCompleted(
            StepRequest request,
            StepRun actorRun,
            EvaluationResponse response);

    /**
     * Returns a callback that invokes this callback followed by {@code after} for each
     * lifecycle event. If this callback throws, {@code after} is not invoked.
     */
    default EvaluationStepRunnerCallback andThen(EvaluationStepRunnerCallback after) {
        Objects.requireNonNull(after, "after");
        val before = this;
        return new EvaluationStepRunnerCallback() {
            @Override
            public void evaluationStarted(StepRequest request, StepRun actorRun) {
                before.evaluationStarted(request, actorRun);
                after.evaluationStarted(request, actorRun);
            }

            @Override
            public void evaluationSkipped(StepRequest request, StepRun actorRun, String reason) {
                before.evaluationSkipped(request, actorRun, reason);
                after.evaluationSkipped(request, actorRun, reason);
            }

            @Override
            public void evaluationFailed(StepRequest request, StepRun actorRun, Throwable cause) {
                before.evaluationFailed(request, actorRun, cause);
                after.evaluationFailed(request, actorRun, cause);
            }

            @Override
            public void evaluationCompleted(
                    StepRequest request,
                    StepRun actorRun,
                    EvaluationResponse response) {
                before.evaluationCompleted(request, actorRun, response);
                after.evaluationCompleted(request, actorRun, response);
            }
        };
    }
}
