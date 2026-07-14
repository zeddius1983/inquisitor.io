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

/**
 * Runs a single step and returns the LLM's verdict together with the tool-call trace
 * it produced (a {@link StepRun}).
 *
 * <p>This is the seam between {@link ScenarioExecutor}'s orchestration (deterministic
 * and unit-testable) and the non-deterministic LLM call. The production implementation
 * is {@link LlmStepRunner}; a decorating runner can wrap it to score how well-grounded each
 * verdict is; tests substitute a fake.
 *
 * <p>All steps of one scenario are run with the same {@code conversationId} so the
 * underlying chat memory carries earlier tool results forward.
 */
@FunctionalInterface
public interface StepRunner {

    StepRun run(StepRequest request);
}
