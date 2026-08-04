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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;

/** Thread-safe cache of the first provider-reported actor/judge model names. */
public class ModelRegistry {

    private final Map<ModelRole, String> models = new ConcurrentHashMap<>();

    /**
     * Records the first nonblank model reported for a role.
     *
     * @return the newly resolved model, or empty if unresolved/already resolved
     */
    public Optional<String> resolve(
            ModelRole role,
            @Nullable ChatResponseMetadata metadata) {
        if (metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()) {
            return Optional.empty();
        }
        val actual = metadata.getModel().strip();
        return models.putIfAbsent(role, actual) == null
                ? Optional.of(actual)
                : Optional.empty();
    }

    /** Returns the first provider-reported actual model for a role, when observed. */
    public Optional<String> actualModel(ModelRole role) {
        return Optional.ofNullable(models.get(role));
    }
}
