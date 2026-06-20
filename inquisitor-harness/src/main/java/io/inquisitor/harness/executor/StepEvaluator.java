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

import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;

/**
 * Evaluates a single {@link Step} and returns the LLM's {@link StepVerdict}.
 *
 * <p>This is the seam between {@link ScenarioExecutor}'s orchestration (which is
 * deterministic and unit-testable) and the non-deterministic LLM call. The
 * production implementation is {@link ChatClientStepEvaluator}; tests substitute
 * a fake.
 *
 * <p>All steps of one scenario are evaluated with the same {@code conversationId}
 * so the underlying chat memory carries earlier tool results forward.
 */
@FunctionalInterface
public interface StepEvaluator {

    /**
     * @param conversationId stable id for the scenario's conversation
     * @param scenario       the scenario being evaluated (for shared context)
     * @param step           the step to perform and verify
     */
    StepVerdict evaluate(String conversationId, Scenario scenario, Step step);
}
