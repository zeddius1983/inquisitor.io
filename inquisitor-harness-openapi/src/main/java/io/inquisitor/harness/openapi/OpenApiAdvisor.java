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

package io.inquisitor.harness.openapi;

import lombok.val;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.core.Ordered;

/**
 * Injects the application's OpenAPI description into the system prompt of every model
 * call, so the model can choose endpoints itself.
 *
 * <p>Implemented as a Spring AI {@link BaseAdvisor}: {@link #before} augments the
 * request's system message with the spec — the same shape RAG's
 * {@code QuestionAnswerAdvisor} uses to inject retrieved context. The spec comes from
 * an {@link OpenApiSpecProvider}, which fails fast if it cannot be obtained.
 */
public class OpenApiAdvisor implements BaseAdvisor {

    /**
     * Provisional precedence: run early (before the logging advisor, so the augmented
     * request is what gets logged). Touching only the system message, it does not
     * conflict with the chat-memory advisor's conversation messages. A tuning point.
     */
    public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final OpenApiSpecProvider specProvider;
    private final int order;

    public OpenApiAdvisor(OpenApiSpecProvider specProvider) {
        this(specProvider, DEFAULT_ORDER);
    }

    public OpenApiAdvisor(OpenApiSpecProvider specProvider, int order) {
        this.specProvider = specProvider;
        this.order = order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        val yaml = specProvider.spec(); // throws if discovery is enabled but the spec is unobtainable
        // The String overload of augmentSystemMessage *replaces* the text; use the function
        // form so the spec is appended to the harness's base system prompt, not overwriting it.
        val augmented = request.prompt().augmentSystemMessage(
                systemMessage -> systemMessage.mutate().text(systemMessage.getText() + section(yaml)).build());
        return request.mutate().prompt(augmented).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        return response;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getName() {
        return "openApiAdvisor";
    }

    private static String section(String yaml) {
        return "\n# Application API description (OpenAPI)\n\n"
                + "The application under test exposes the following OpenAPI description. Use it to "
                + "choose the correct endpoints, HTTP methods, paths, and request/response shapes "
                + "when performing a step. Make real calls with the httpRequest tool; never invent "
                + "endpoints or responses.\n\n"
                + "```yaml\n" + yaml + "\n```\n";
    }
}
