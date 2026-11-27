/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.inquisitor.harness.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import io.inquisitor.harness.executor.*;
import io.inquisitor.harness.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class SupervisedStepRunnerTest {
    private static final String PASS = """
            {"outcome":"PASS","reasoning":"Verified the assertion","evidence":["actual response"]}
            """;
    private static final String FAIL = """
            {"outcome":"FAIL","reasoning":"The observed balance is wrong","evidence":["actual response"]}
            """;

    @ParameterizedTest
    @ValueSource(strings = {"checkAccounts", "truncateDatabase", "verifyBalance"})
    void addsMissingWorkAndRecordsEveryRound(String toolName) {
        val tool = new CountingTool(toolName);
        val model = new ScriptedModel(response(PASS), toolCall(toolName), response(PASS));
        val recorder = new StepEvaluationRecorder();
        val reviews = new AtomicInteger();
        StepReviewer reviewer = (request, attempt, ledger) -> {
            if (reviews.getAndIncrement() == 0) {
                assertThat(ledger).isEmpty();
                return correct("Invoke " + toolName + " to establish the assertion");
            }
            assertThat(ledger).hasSize(1);
            assertThat(ledger.getFirst().toolName()).isEqualTo(toolName);
            return accept();
        };
        val run = new SupervisedStepRunner(actor(model, tool), reviewer, 2, recorder).run(request());

        assertThat(run.verdict().passed()).isTrue();
        assertThat(tool.calls.get()).isEqualTo(1);
        assertThat(run.attempts()).hasSize(2);
        assertThat(run.attempts().getFirst().toolCalls()).isEmpty();
        assertThat(run.attempts().getLast().toolCalls()).hasSize(1);
        assertThat(recorder.records()).hasSize(1);
        assertThat(recorder.records().getFirst().correctionCount()).isEqualTo(1);
        assertThat(recorder.records().getFirst().acceptedInitially()).isFalse();
    }

    @Test
    void correctionSeesSuccessfulWriteAndAddsOnlyVerification() {
        val write = new CountingTool("createAccount");
        val read = new CountingTool("readAccount");
        val model = new ScriptedModel(toolCall("createAccount"), response(PASS),
                toolCall("readAccount"), response(FAIL));
        val recorder = new StepEvaluationRecorder();
        val calls = new AtomicInteger();
        val run = new SupervisedStepRunner(actor(model, write, read), (request, attempt, ledger) -> {
            if (calls.getAndIncrement() == 0) {
                assertThat(ledger).hasSize(1);
                return correct("Creation succeeded in #0. Read the account to verify its balance.");
            }
            assertThat(ledger).extracting(ToolCallRecord::order).containsExactly(0, 1);
            return accept();
        }, 2, recorder).run(request());

        assertThat(run.verdict().outcome()).isEqualTo(Outcome.FAIL);
        assertThat(write.calls.get()).isEqualTo(1);
        assertThat(read.calls.get()).isEqualTo(1);
        assertThat(model.prompts.get(2).getUserMessage().getText())
                .contains("Creation succeeded", "#0 createAccount", "real result");
        assertThat(run.attempts().getFirst().toolCalls()).hasSize(1);
        assertThat(run.attempts().getLast().toolCalls()).hasSize(1);
    }

    @Test
    void malformedThenUnsupportedAnswerSharesOneBudget() {
        val model = new ScriptedModel(response("broken JSON"), response(PASS));
        val recorder = new StepEvaluationRecorder();
        val reviews = new AtomicInteger();
        val runner = new SupervisedStepRunner(actor(model), (request, attempt, ledger) -> {
            reviews.incrementAndGet();
            return correct("No evidence; invoke the API");
        }, 1, recorder);

        assertThatThrownBy(() -> runner.run(request())).isInstanceOf(UnresolvedStepException.class)
                .hasMessageContaining("budget exhausted");
        assertThat(reviews.get()).isEqualTo(1);
        assertThat(model.prompts).hasSize(2);
        assertThat(model.prompts.getLast().getUserMessage().getText()).contains("schema", "broken JSON");
        val record = recorder.records().getFirst();
        assertThat(record.unresolved()).isTrue();
        assertThat(record.rounds()).hasSize(2);
        assertThat(record.rounds().getFirst().attempt().rawResponse()).isEqualTo("broken JSON");
        assertThat(record.rounds().getFirst().review()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken JSON", "", "null", "{\"reasoning\":\"No outcome\",\"evidence\":[]}"})
    void repairsInvalidVerdictsBeforeReview(String invalid) {
        val model = new ScriptedModel(response(invalid), response(PASS));
        val reviews = new AtomicInteger();
        val run = new SupervisedStepRunner(actor(model), (request, attempt, ledger) -> {
            reviews.incrementAndGet();
            return accept();
        }, 1, new StepEvaluationRecorder()).run(request());
        assertThat(reviews.get()).isEqualTo(1);
        assertThat(run.attempts()).hasSize(2);
        assertThat(run.synthetic()).isFalse();
    }

    @Test
    void acceptsGroundedApplicationFailureWithoutRetrying() {
        val model = new ScriptedModel(response(FAIL));
        val recorder = new StepEvaluationRecorder();
        val run = new SupervisedStepRunner(actor(model), (request, attempt, ledger) -> accept(),
                2, recorder).run(request());
        assertThat(run.verdict().outcome()).isEqualTo(Outcome.FAIL);
        assertThat(model.prompts).hasSize(1);
        assertThat(recorder.records().getFirst().acceptedInitially()).isTrue();
    }

    @Test
    void supervisionDoesNotEmitObservationOnlyEvaluationEvents() {
        val callback = mock(EvaluationStepRunnerCallback.class, CALLS_REAL_METHODS);
        val runner = new SupervisedStepRunner(actor(new ScriptedModel(response(PASS))),
                (request, attempt, ledger) -> accept(), 0, callback);

        val run = runner.run(request());

        verify(callback).reviewCompleted(org.mockito.ArgumentMatchers.eq(request()),
                org.mockito.ArgumentMatchers.any(StepReviewRound.class));
        verify(callback).supervisionCompleted(org.mockito.ArgumentMatchers.eq(request()),
                org.mockito.ArgumentMatchers.eq(run), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(""));
        verifyNoMoreInteractions(callback);
    }

    @Test
    void stopsWhenExperimentNeedsFreshFixture() {
        val model = new ScriptedModel(response(PASS));
        val recorder = new StepEvaluationRecorder();
        val runner = new SupervisedStepRunner(actor(model), (request, attempt, ledger) ->
                new StepReview(StepReview.Decision.STOP, EvaluationCategory.CONTRADICTED,
                        List.of("Setup happened after the assertion"), List.of(), "Fresh fixture required"),
                2, recorder);
        assertThatThrownBy(() -> runner.run(request())).isInstanceOf(UnresolvedStepException.class)
                .hasMessage("Fresh fixture required");
        assertThat(model.prompts).hasSize(1);
        assertThat(recorder.records().getFirst().unresolved()).isTrue();
    }

    @Test
    void judgeFailureNeverAcceptsTheActorClaim() {
        val recorder = new StepEvaluationRecorder();
        val runner = new SupervisedStepRunner(actor(new ScriptedModel(response(PASS))),
                (request, attempt, ledger) -> { throw new IllegalStateException("Bad judge JSON"); },
                2, recorder);
        assertThatThrownBy(() -> runner.run(request())).isInstanceOf(UnresolvedStepException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(recorder.records().getFirst().unresolved()).isTrue();
        assertThat(recorder.records().getFirst().rounds().getFirst().feedback()).contains("Bad judge JSON");
    }

    @Test
    void failedToolRecordsUnknownEffectsAndAbortsWithoutReplaying() {
        val tool = new CountingTool("write");
        tool.failure = true;
        val model = new ScriptedModel(toolCall("write"));
        val recorder = new StepEvaluationRecorder();
        val runner = new SupervisedStepRunner(actor(model, tool), (request, attempt, ledger) -> {
            throw new AssertionError("A failed actor call must not be reviewed as a completed execution");
        }, 2, recorder);
        assertThatThrownBy(() -> runner.run(request())).isInstanceOf(UnresolvedStepException.class);
        assertThat(tool.calls.get()).isEqualTo(1);
        assertThat(recorder.records().getFirst().toolCalls().getFirst()).contains("THREW", "effects unknown");
    }

    @Test
    void separateConversationsNeverShareLedgers() {
        val model = new ScriptedModel(response(PASS), response(PASS));
        val actor = actor(model);
        val first = actor.openConversation(request());
        val second = actor.openConversation(new StepRequest("other", "Other step", request().scenario(), request().step()));
        first.execute();
        second.execute();
        assertThat(first.attempts()).hasSize(1);
        assertThat(second.attempts()).hasSize(1);
        assertThat(model.prompts.getLast().getInstructions().toString()).doesNotContain("Check the account");
    }

    @Test
    void concurrentScenariosKeepTheirToolEvidenceSeparate() throws Exception {
        val tool = new CountingTool("check");
        ChatModel model = new ChatModel() {
            @Override public ChatOptions getOptions() {
                return OpenAiChatOptions.builder().build();
            }
            @Override public ChatResponse call(Prompt prompt) {
                if (prompt.getInstructions().stream()
                        .anyMatch(org.springframework.ai.chat.messages.ToolResponseMessage.class::isInstance)) {
                    return response(PASS);
                }
                val owner = prompt.getUserMessage().getText().contains("first") ? "first" : "second";
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("check", "function", "check",
                                "{\"owner\":\"" + owner + "\"}"))).build())));
            }
        };
        val recorder = new StepEvaluationRecorder();
        val runner = new SupervisedStepRunner(actor(model, tool), (request, attempt, ledger) -> {
            assertThat(ledger).hasSize(1);
            assertThat(ledger.getFirst().arguments()).contains(request.conversationId());
            return accept();
        }, 1, recorder);
        try (val executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            val first = executor.submit(() -> runner.run(new StepRequest(
                    "first", "Check first", request().scenario(), request().step())));
            val second = executor.submit(() -> runner.run(new StepRequest(
                    "second", "Check second", request().scenario(), request().step())));
            assertThat(first.get().toolCalls()).hasSize(1);
            assertThat(second.get().toolCalls()).hasSize(1);
        }
        assertThat(recorder.records()).hasSize(2);
        assertThat(tool.calls.get()).isEqualTo(2);
    }

    private static StepReview correct(String feedback) {
        return new StepReview(StepReview.Decision.CORRECT, EvaluationCategory.UNSUPPORTED,
                List.of(feedback), List.of(), feedback);
    }

    private static StepReview accept() {
        return new StepReview(StepReview.Decision.ACCEPT, EvaluationCategory.GROUNDED,
                List.of(), List.of(), "Supported");
    }

    private static StepRequest request() {
        val step = new Step(1, "Check", "Check the account");
        return StepRequest.of("scenario", new Scenario("Scenario", "Banking", List.of(step), null), step);
    }

    private static LlmStepRunner actor(ChatModel model, CountingTool... tools) {
        val callbacks = java.util.Arrays.stream(tools).map(RecordingToolCallback::new).toArray(ToolCallback[]::new);
        return new LlmStepRunner(ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
                .defaultTools((Object[]) callbacks).build());
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static ChatResponse toolCall(String name) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + name, "function", name, "{}"))).build())));
    }

    private static class ScriptedModel implements ChatModel {
        private final ArrayDeque<ChatResponse> responses;
        private final List<Prompt> prompts = new ArrayList<>();
        ScriptedModel(ChatResponse... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }
        @Override public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            return responses.removeFirst();
        }
        @Override public ChatOptions getOptions() {
            return OpenAiChatOptions.builder().model("scripted").build();
        }
    }

    private static class CountingTool implements ToolCallback {
        private final String name;
        private final AtomicInteger calls = new AtomicInteger();
        private boolean failure;
        CountingTool(String name) { this.name = name; }
        @Override public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
        }
        @Override public String call(String input) {
            calls.incrementAndGet();
            if (failure) { throw new IllegalStateException("Disconnected after write"); }
            return "real result";
        }
    }
}
