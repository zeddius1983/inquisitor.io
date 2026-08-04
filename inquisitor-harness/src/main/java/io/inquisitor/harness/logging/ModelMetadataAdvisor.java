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

package io.inquisitor.harness.logging;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

/** Observes real model responses and caches the first reported model for a role. */
public final class ModelMetadataAdvisor implements BaseAdvisor {

    private static final int ORDER = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50;

    private final ModelRole role;
    private final ModelRegistry registry;

    public ModelMetadataAdvisor(
            ModelRole role,
            ModelRegistry registry) {
        this.role = role;
        this.registry = registry;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (response.chatResponse() != null) {
            registry.resolve(role, response.chatResponse().getMetadata());
        }
        return response;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
