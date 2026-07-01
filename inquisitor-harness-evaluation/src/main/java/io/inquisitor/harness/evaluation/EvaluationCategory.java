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

/**
 * How well a step's verdict is supported by the actual tool-call trace, as judged by
 * the credibility evaluator. A discrete classification (rather than a free-form number)
 * keeps a weaker judge model stable; the score mapping stays under our control.
 */
public enum EvaluationCategory {

    /** Every action and value the verdict relies on is present in, and consistent with, the trace. */
    GROUNDED(1.0),

    /** Plausible, but a cited value or required action is missing from the trace. */
    PARTIALLY_GROUNDED(0.5),

    /** The verdict is not entailed by any tool result. */
    UNSUPPORTED(0.2),

    /** The trace contradicts the verdict, or a claimed action never happened. */
    CONTRADICTED(0.0);

    private final double score;

    EvaluationCategory(double score) {
        this.score = score;
    }

    /** The 0.0–1.0 credibility score this category maps to. */
    public double score() {
        return score;
    }
}
