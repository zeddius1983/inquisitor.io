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

/**
 * The LLM's structured judgement for a step: the {@link Outcome}, its reasoning,
 * and the tool responses it relied on as evidence.
 *
 * @param outcome   PASS or FAIL
 * @param reasoning a short explanation of the verdict
 * @param evidence  references to the tool responses that justify it
 */
public record StepVerdict(Outcome outcome, String reasoning, List<String> evidence) {

    public StepVerdict {
        evidence = List.copyOf(evidence);
    }

    public boolean passed() {
        return outcome == Outcome.PASS;
    }
}
