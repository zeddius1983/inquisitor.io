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

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A parsed test scenario: a name, optional shared context, and one or more
 * ordered {@link Step steps} executed over a single LLM conversation.
 *
 * @param name            the scenario title (from the markdown {@code # } heading)
 * @param description     shared context shown before the first step; empty for a
 *                        single-step scenario
 * @param steps           the ordered, non-empty list of steps
 * @param source          where the scenario was loaded from (location / URI), if known
 * @param expectedOutcome the outcome this run of the scenario is expected to produce.
 *                        {@link Outcome#PASS} normally (the parser always produces it —
 *                        markdown has no expectation syntax); {@link Outcome#FAIL} for a
 *                        fault-detection run, where a failing step is the success
 *                        condition (set by the JUnit layer from
 *                        {@code @Scenario(expect = FAIL)})
 */
@Builder
@With
public record Scenario(String name, String description, List<Step> steps, @Nullable String source,
                       Outcome expectedOutcome) {

    public Scenario {
        steps = List.copyOf(steps);
    }

    /** A scenario expected to pass — the parser's and the ordinary run's default. */
    public Scenario(String name, String description, List<Step> steps, @Nullable String source) {
        this(name, description, steps, source, Outcome.PASS);
    }

    /** Whether this scenario has more than one step. */
    public boolean isMultiStep() {
        return steps.size() > 1;
    }
}
