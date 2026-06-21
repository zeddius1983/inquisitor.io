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
import io.inquisitor.harness.model.ScenarioResult;
import lombok.val;

/**
 * Evaluates {@link Scenario scenarios} step by step over a {@link StepEvaluator}.
 *
 * <p>Use {@link #evaluate(Scenario)} to run a whole scenario, or
 * {@link #start(Scenario)} to drive it one step at a time (e.g. to report each
 * step as its own JUnit test). Both share the same fail-fast semantics via
 * {@link ScenarioEvaluation}.
 */
public class ScenarioExecutor {

    private final StepEvaluator evaluator;

    public ScenarioExecutor(StepEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /** Evaluates every step, stopping at the first failure. */
    public ScenarioResult evaluate(Scenario scenario) {
        val evaluation = start(scenario);
        while (evaluation.hasNext()) {
            evaluation.next();
        }
        return evaluation.result();
    }

    /** Begins a step-at-a-time evaluation over a fresh conversation. */
    public ScenarioEvaluation start(Scenario scenario) {
        return new ScenarioEvaluation(evaluator, scenario);
    }
}
