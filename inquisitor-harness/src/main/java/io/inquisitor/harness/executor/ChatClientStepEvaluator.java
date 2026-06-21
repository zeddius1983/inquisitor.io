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

import java.util.List;

import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import lombok.val;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * {@link StepEvaluator} backed by a Spring AI {@link ChatClient}.
 *
 * <p>Each step is sent as a user message scoped to the scenario's conversation id,
 * so the configured {@code MessageChatMemoryAdvisor} replays prior steps (and
 * their tool results) into context. The response is mapped straight onto
 * {@link StepVerdict} via structured output.
 *
 * <p>The {@link ChatClient} must be pre-configured with the harness system prompt
 * ({@link HarnessSystemPrompt#TEXT}), a chat-memory advisor, and the tools the
 * model may call (built-in plus any user-supplied ones) — see the starter's
 * autoconfiguration.
 */
public class ChatClientStepEvaluator implements StepEvaluator {

    private final ChatClient chatClient;

    public ChatClientStepEvaluator(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public StepVerdict evaluate(String conversationId, Scenario scenario, Step step) {
        val verdict = chatClient.prompt()
                .user(userMessage(scenario, step))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(StepVerdict.class);
        return verdict != null
                ? verdict
                : new StepVerdict(Outcome.FAIL, "The model did not return a verdict.", List.of());
    }

    private static String userMessage(Scenario scenario, Step step) {
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
