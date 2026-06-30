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

/**
 * Everything a {@link StepRunner} needs to evaluate one step: the conversation id
 * (shared across a scenario's steps), the fully-rendered user message to send, and
 * the originating scenario/step for context.
 *
 * <p>The message is built once (by {@link StepMessages}) so a decorating runner can
 * reuse it — e.g. as the {@code userText} of a credibility evaluation — without
 * rebuilding it.
 */
public record StepRequest(String conversationId, String userMessage, Scenario scenario, Step step) {

    /** Builds a request with the user message rendered from the scenario and step. */
    public static StepRequest of(String conversationId, Scenario scenario, Step step) {
        return new StepRequest(conversationId, StepMessages.userMessage(scenario, step), scenario, step);
    }
}
