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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.StepVerdict;
import io.inquisitor.harness.model.ToolCallRecord;
import io.inquisitor.harness.tool.TraceKeys;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import tools.jackson.core.JacksonException;

/**
 * {@link StepRunner} backed by a Spring AI {@link ChatClient}.
 *
 * <p>Each step is sent as a user message scoped to the scenario's conversation id, so
 * the configured {@code MessageChatMemoryAdvisor} replays prior steps (and their tool
 * results) into context. The response is mapped straight onto {@link StepVerdict} via
 * structured output.
 *
 * <p>A fresh tool-call ledger is threaded into every call through Spring AI's
 * {@code ToolContext}. When the registered tools are wrapped to record (only in the
 * evaluation context — see the starter's autoconfiguration), they append to that
 * ledger, which is then surfaced on the returned {@link StepRun}. In a normal run the
 * tools are not wrapped, so the ledger stays empty and there is zero overhead.
 *
 * <p>The {@link ChatClient} must be pre-configured with the harness system prompt
 * ({@link HarnessSystemPrompt#TEXT}), a chat-memory advisor, and the tools the model
 * may call — see the starter's autoconfiguration.
 */
@Slf4j
public class LlmStepRunner implements StepRunner {

    private final ChatClient chatClient;

    public LlmStepRunner(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public StepRun run(StepRequest request) {
        val scenario = request.scenario();
        val step = request.step();
        val number = step.index();
        val total = scenario.steps().size();
        log.debug("[{}] step {}/{} - RUN: {}", scenario.name(), number, total, step.title());

        val ledger = Collections.synchronizedList(new ArrayList<ToolCallRecord>());
        val startedNanos = System.nanoTime();
        var synthetic = false;
        StepVerdict verdict;
        try {
            verdict = chatClient.prompt()
                    .user(request.userMessage())
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                    .toolContext(Map.of(TraceKeys.LEDGER, ledger))
                    .call()
                    .entity(StepVerdict.class);
        } catch (JacksonException e) {
            // A flaky model can return an empty or malformed completion; the structured-output
            // converter then throws. Degrade to a FAIL at this step so the scenario keeps its
            // fail-fast semantics instead of aborting the whole run. Genuine transport errors
            // are not JacksonException and still propagate.
            log.debug("[{}] step {}/{} — unparseable model response, treating as FAIL: {}",
                    scenario.name(), number, total, e.getMessage());
            verdict = new StepVerdict(Outcome.FAIL,
                    "The model returned an empty or unparseable response.", List.of());
            synthetic = true;
        }
        val elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
        StepVerdict resolved;
        if (verdict != null) {
            resolved = verdict;
        } else {
            resolved = new StepVerdict(Outcome.FAIL, "The model did not return a verdict.", List.of());
            synthetic = true;
        }

        log.debug("[{}] step {}/{} — {} in {} ms: {}", scenario.name(), number, total,
                resolved.outcome(), elapsed.toMillis(), resolved.reasoning());
        return new StepRun(resolved, List.copyOf(ledger), elapsed, synthetic);
    }
}
