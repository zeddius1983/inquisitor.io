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

import java.util.List;
import java.util.Optional;

/**
 * The aggregate result of evaluating a {@link Scenario}.
 *
 * <p>With fail-fast evaluation, {@code results} holds one {@link StepResult} per
 * step that actually ran: every step up to and including the first failure, or
 * all steps when the scenario passes.
 */
public record ScenarioResult(Scenario scenario, List<StepResult> results) {

    public ScenarioResult {
        results = List.copyOf(results);
    }

    /** Whether every step ran and passed. */
    public boolean passed() {
        return results.size() == scenario.steps().size() && results.stream().allMatch(StepResult::passed);
    }

    /** The first failing step result, if any. */
    public Optional<StepResult> firstFailure() {
        return results.stream().filter(r -> !r.passed()).findFirst();
    }
}
