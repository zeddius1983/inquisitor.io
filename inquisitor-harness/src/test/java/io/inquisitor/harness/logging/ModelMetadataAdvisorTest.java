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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;

class ModelMetadataAdvisorTest {

    @Test
    void cachesOnlyTheFirstNonblankModelFromRealResponses() {
        val registry = new ModelRegistry();
        val advisor = new ModelMetadataAdvisor(ModelRole.ACTOR, registry);
        val chain = mock(AdvisorChain.class);

        advisor.after(response(""), chain);
        advisor.after(response("actual-model"), chain);
        advisor.after(response("ignored-later-model"), chain);

        assertThat(registry.actualModel(ModelRole.ACTOR)).contains("actual-model");
    }

    @Test
    void ignoresResponsesWithoutChatMetadata() {
        val registry = new ModelRegistry();
        val advisor = new ModelMetadataAdvisor(ModelRole.JUDGE, registry);

        advisor.after(ChatClientResponse.builder().build(), mock(AdvisorChain.class));

        assertThat(registry.actualModel(ModelRole.JUDGE)).isEmpty();
    }

    private static ChatClientResponse response(String model) {
        val metadata = mock(ChatResponseMetadata.class);
        when(metadata.getModel()).thenReturn(model);
        val response = new ChatResponse(List.of(), metadata);
        return ChatClientResponse.builder().chatResponse(response).build();
    }
}
