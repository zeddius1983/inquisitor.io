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

package io.inquisitor.harness.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.inquisitor.harness.model.ToolCallRecord;
import io.inquisitor.harness.tool.TraceKeys;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class RecordingToolCallbackTest {

    @Test
    void recordsEachCallWhenLedgerPresent() {
        val ledger = new ArrayList<ToolCallRecord>();
        val recording = new RecordingToolCallback(new FakeToolCallback("httpRequest", "HTTP 200"));
        val context = new ToolContext(Map.of(TraceKeys.LEDGER, ledger));

        val first = recording.call("{\"path\":\"/a\"}", context);
        val second = recording.call("{\"path\":\"/b\"}", context);

        assertThat(first).isEqualTo("HTTP 200");
        assertThat(second).isEqualTo("HTTP 200");
        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(0).toolName()).isEqualTo("httpRequest");
        assertThat(ledger.get(0).arguments()).isEqualTo("{\"path\":\"/a\"}");
        assertThat(ledger.get(0).result()).isEqualTo("HTTP 200");
        assertThat(ledger).extracting(ToolCallRecord::order).containsExactly(0, 1);
    }

    @Test
    void passesThroughWhenNoLedgerInContext() {
        val recording = new RecordingToolCallback(new FakeToolCallback("sqlQuery", "0 row(s): []"));

        assertThat(recording.call("SELECT 1", new ToolContext(Map.of()))).isEqualTo("0 row(s): []");
        assertThat(recording.call("SELECT 1")).isEqualTo("0 row(s): []");
    }

    @Test
    void delegatesToolDefinition() {
        val recording = new RecordingToolCallback(new FakeToolCallback("sqlQuery", "x"));
        assertThat(recording.getToolDefinition().name()).isEqualTo("sqlQuery");
    }

    /** Minimal {@link ToolCallback} returning a fixed result. */
    private record FakeToolCallback(String name, String result) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
        }

        @Override
        public String call(String toolInput) {
            return result;
        }

        @Override
        public String call(String toolInput, @Nullable ToolContext toolContext) {
            return result;
        }
    }
}
