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

import java.time.Duration;
import java.util.List;

import io.inquisitor.harness.model.ToolCallRecord;
import io.inquisitor.harness.tool.TraceKeys;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * Wraps a {@link ToolCallback} to append a {@link ToolCallRecord} for every invocation,
 * giving the credibility evaluator an out-of-band trace of what the model actually did.
 *
 * <p>The ledger is found in the {@code ToolContext} under {@link TraceKeys#LEDGER}; the
 * runner installs a fresh one per step. If no ledger is present (a normal, non-evaluated
 * run) the wrapper is a transparent pass-through. Wrapping is purely observational — the
 * delegate's behaviour and result are unchanged.
 */
public class RecordingToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public RecordingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        val ledger = ledgerOf(toolContext);
        val start = System.nanoTime();
        val result = delegate.call(toolInput, toolContext);
        if (ledger != null) {
            ledger.add(new ToolCallRecord(
                    getToolDefinition().name(),
                    toolInput,
                    result,
                    ledger.size(),
                    Duration.ofNanos(System.nanoTime() - start)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable List<ToolCallRecord> ledgerOf(@Nullable ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        val ledger = toolContext.getContext().get(TraceKeys.LEDGER);
        return ledger instanceof List ? (List<ToolCallRecord>) ledger : null;
    }
}
