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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.model.Step;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Exercises {@link LlmStepRunner} against the real local model with <em>stub</em>
 * tools (canned HTTP responses), validating that the model does tool-calling +
 * structured output and that state threads across steps.
 *
 * <p>Gated: set {@code INQUISITOR_LLM_IT=true} with a local OpenAI-compatible
 * server running at {@code http://127.0.0.1:8000} to run it.
 */
@EnabledIfEnvironmentVariable(named = "INQUISITOR_LLM_IT", matches = "true")
class LlmStepRunnerIT {

    private static final String BASE_URL = "http://127.0.0.1:8000";
    private static final String MODEL = "gemma-4-31B-it-QAT-Q4_0";

    @Test
    void evaluatesAMultiStepScenarioThreadingTheCreatedId() {
        val model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(BASE_URL)
                        .apiKey("llama")
                        .model(MODEL)
                        .temperature(0.0d)
                        .build())
                .build();

        val tools = new StubHttpTool();
        val chatClient = ChatClient.builder(model)
                .defaultSystem(HarnessSystemPrompt.TEXT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build(),
                        // Logs request/response at DEBUG; gated by logback-test.xml (INQUISITOR_LLM_LOG).
                        new SimpleLoggerAdvisor())
                .defaultTools(tools)
                .build();

        ScenarioResult result = getScenarioResult(chatClient);

        result.results().forEach(r ->
                System.out.printf("%s -> %s (%s)%n", r.step().title(), r.verdict().outcome(), r.verdict().reasoning()));

        assertThat(result.passed()).isTrue();
        assertThat(tools.records).containsExactly(
                "POST /orders",
                "GET /orders/42",
                "DELETE /orders/42",
                "GET /orders/42"
        );

    }

    private static @NonNull ScenarioResult getScenarioResult(ChatClient chatClient) {
        val executor = new ScenarioExecutor(new LlmStepRunner(chatClient));

        val scenario = new Scenario(
                "Order lifecycle",
                "Exercises the order API end to end via the httpRequest tool.",
                List.of(
                        new Step(1, "Create an order",
                                "Create an order via POST to /orders with an empty JSON body."),
                        new Step(2, "Load the order",
                                "Load the order created in step 1 via GET /orders/{id}. Verify its status is CREATED."),
                        new Step(3, "Delete the order",
                                "Delete the order via DELETE /orders/{id}. Verify the response status is DELETED."),
                        new Step(4, "Reload the order",
                                "Load the deleted order again via GET /orders/{id}. Verify that nothing is returned.")),
                "order-lifecycle.md");

        return executor.execute(scenario);
    }

    /** Canned HTTP tool — simulates an order resource without a real server. */
    static final class StubHttpTool {

        final List<String> records = new CopyOnWriteArrayList<>();

        @Tool(description = "Make an HTTP request to the application under test and return the response body")
        String httpRequest(
                @ToolParam(description = "HTTP method, e.g. GET, POST, DELETE") String method,
                @ToolParam(description = "request path, e.g. /orders or /orders/42") String path,
                @ToolParam(description = "JSON request body, or an empty string") String body) {
            val request = method + " " + path;
            records.add(request);
            val response = switch (request) {
                case "POST /orders" -> "{\"id\": 42, \"status\": \"CREATED\"}";
                case "GET /orders/42" -> records.contains("DELETE /orders/42") ? "{}" : "{\"id\": 42, \"status\": \"CREATED\"}";
                case "DELETE /orders/42" -> "{\"id\": 42, \"status\": \"DELETED\"}";
                default -> "{\"error\": \"unexpected\"}";
            };
            System.out.println(request + " -> " + response);
            return response;
        }

    }
}
