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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class LlmStepRunnerTest {

    private static final String VALID_VERDICT = """
            {"outcome":"PASS","reasoning":"verified","evidence":["stub"]}
            """;

    @Test
    void returnsStructuredVerdictAndEmitsSemanticLifecycleEvents() {
        val logger = new RecordingLlmLogger();

        val run = runner(new StubChatModel(response(VALID_VERDICT)), logger).run(request());

        assertThat(run.verdict().outcome()).isEqualTo(Outcome.PASS);
        assertThat(run.verdict().reasoning()).isEqualTo("verified");
        assertThat(run.verdict().evidence()).containsExactly("stub");
        assertThat(run.synthetic()).isFalse();
        assertThat(logger.events).containsExactly("started:Step", "completed:PASS");
        assertThat(logger.completedRun).isSameAs(run);
    }

    @Test
    void malformedResponseBecomesSyntheticFailureAndIsLogged() {
        val logger = new RecordingLlmLogger();

        val run = runner(new StubChatModel(response("not-json")), logger).run(request());

        assertThat(run.synthetic()).isTrue();
        assertThat(run.verdict().outcome()).isEqualTo(Outcome.FAIL);
        assertThat(run.verdict().reasoning())
                .isEqualTo("The model returned an empty or unparseable response.");
        assertThat(logger.events)
                .containsExactly("started:Step", "unparseable:Step", "completed:FAIL");
    }

    private static LlmStepRunner runner(ChatModel model, LlmStepRunnerCallback callback) {
        return new LlmStepRunner(ChatClient.builder(model).build(), callback);
    }

    private static StepRequest request() {
        val scenario = new Scenario(
                "Scenario", "", List.of(new Step(1, "Step", "Verify it")), null);
        return StepRequest.of("conversation", scenario, scenario.steps().getFirst());
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private static final class RecordingLlmLogger implements LlmStepRunnerCallback {

        private final List<String> events = new java.util.ArrayList<>();
        private StepRun completedRun;

        @Override
        public void stepStarted(StepRequest request) {
            events.add("started:" + request.step().title());
        }

        @Override
        public void responseUnparseable(StepRequest request, Throwable cause) {
            events.add("unparseable:" + request.step().title());
        }

        @Override
        public void stepCompleted(StepRequest request, StepRun run) {
            completedRun = run;
            events.add("completed:" + run.verdict().outcome());
        }
    }

    private static final class StubChatModel implements ChatModel {

        private final Deque<ChatResponse> responses;

        private StubChatModel(ChatResponse... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return responses.removeFirst();
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().model("configured-model").build();
        }
    }
}
