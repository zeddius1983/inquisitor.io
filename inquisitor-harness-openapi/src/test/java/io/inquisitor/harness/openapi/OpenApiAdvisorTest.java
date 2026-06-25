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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

class OpenApiAdvisorTest {

    @Test
    void augmentsTheSystemMessageWithTheSpec() {
        val advisor = new OpenApiAdvisor(() -> "openapi: 3.1.0\npaths: {}");
        val request = new ChatClientRequest(
                new Prompt(List.<Message>of(new SystemMessage("base prompt"), new UserMessage("do the step"))),
                Map.of());

        val result = advisor.before(request, null);

        val system = result.prompt().getSystemMessage().getText();
        assertThat(system)
                .contains("base prompt")                  // original system prompt kept
                .contains("openapi: 3.1.0")               // spec injected
                .contains("Application API description");  // framing preamble
    }
}
