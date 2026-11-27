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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.List;
import io.inquisitor.harness.executor.StepAttempt;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;

class LlmStepReviewerTest {
    @Test
    void reviewsRealTraceWithoutLeakingFaultExpectation() {
        val model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"decision":"ACCEPT","category":"GROUNDED","findings":[],
                 "toolCallReferences":[0],"feedback":"Supported"}
                """));
        val step = new Step(1, "Read", "Check account");
        val scenario = new Scenario("bank", "Account scenario", List.of(step), null).withExpectedOutcome(Outcome.FAIL);
        val request = StepRequest.of("conversation", scenario, step);
        val verdict = new StepVerdict(Outcome.PASS, "Verified", List.of("actual result"));
        val attempt = new StepAttempt(1, "raw claim", verdict, null, List.of(), Duration.ZERO);
        val reviewer = new LlmStepReviewer(ChatClient.builder(model).build());

        assertThat(reviewer.review(request, attempt, List.of(
                new ToolCallRecord("read", "{}", "actual result", 0, Duration.ZERO))).decision())
                .isEqualTo(StepReview.Decision.ACCEPT);
        val captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        val text = captor.getValue().getUserMessage().getText();
        assertThat(text).contains("Account scenario", "raw claim", "#0 read({}) -> actual result")
                .doesNotContain("expectedOutcome", "Expect.FAIL", "faultDetection");
    }

    @Test
    void invalidJudgeOutputAndUnsupportedAcceptanceAreRejected() {
        for (val output : List.of("not-json",
                "{\"decision\":\"ACCEPT\",\"category\":\"UNSUPPORTED\",\"findings\":[],\"toolCallReferences\":[],\"feedback\":\"\"}",
                "{\"decision\":\"ACCEPT\",\"category\":\"GROUNDED\",\"findings\":[],\"toolCallReferences\":[99],\"feedback\":\"\"}")) {
            val model = mock(ChatModel.class);
            when(model.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
            when(model.call(any(Prompt.class))).thenReturn(response(output));
            val step = new Step(1, "Read", "Read account");
            val request = StepRequest.of("c", new Scenario("s", "", List.of(step), null), step);
            val attempt = new StepAttempt(1, "claim",
                    new StepVerdict(Outcome.PASS, "Claim", List.of()), null, List.of(), Duration.ZERO);
            assertThatThrownBy(() -> new LlmStepReviewer(ChatClient.builder(model).build())
                    .review(request, attempt, List.of())).isInstanceOf(RuntimeException.class);
        }
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
