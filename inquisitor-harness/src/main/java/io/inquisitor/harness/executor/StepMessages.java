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
import lombok.val;

/**
 * Renders the user message sent to the model for a step. Extracted so the message is
 * built once and reused — both as the prompt for the actor and as the {@code userText}
 * a credibility evaluator audits.
 *
 * <p>The scenario description is prepended only on the first step; later steps rely on
 * chat memory to carry the context forward.
 */
final class StepMessages {

    private StepMessages() {
    }

    static String userMessage(Scenario scenario, Step step) {
        val message = new StringBuilder();
        if (step.index() == 1 && !scenario.description().isBlank()) {
            message.append("Scenario context:\n").append(scenario.description()).append("\n\n");
        }
        return message
                .append("Step ").append(step.index()).append(": ").append(step.title()).append('\n')
                .append(step.instruction())
                .toString();
    }
}
