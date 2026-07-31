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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;

/** Thread-safe registry of configured and first-observed actor/judge model details. */
public class ModelRegistry {

    private final Map<ModelRole, State> models = new EnumMap<>(ModelRole.class);

    /** Adds or replaces configured settings while retaining already observed metadata. */
    public synchronized void configure(ModelConfiguration configuration) {
        models.compute(configuration.role(), (role, existing) -> existing == null
                ? new State(configuration)
                : existing.withConfiguration(configuration));
    }

    /**
     * Records the first nonblank model reported for a role.
     *
     * @return the newly resolved snapshot, or empty if unresolved/already resolved
     */
    public Optional<ModelSnapshot> resolve(
            ModelRole role,
            @Nullable ChatResponseMetadata metadata) {
        if (metadata == null || metadata.getModel() == null || metadata.getModel().isBlank()) {
            return Optional.empty();
        }
        State state;
        synchronized (this) {
            state = models.computeIfAbsent(role, ModelRegistry::emptyState);
        }
        String actual = metadata.getModel().strip();
        return state.actualModel.compareAndSet(null, actual)
                ? Optional.of(state.snapshot())
                : Optional.empty();
    }

    /** Returns immutable snapshots in actor/judge role order. */
    public synchronized List<ModelSnapshot> snapshots() {
        return models.values().stream().map(State::snapshot).toList();
    }

    /** Returns the first provider-reported actual model for a role, when observed. */
    public synchronized Optional<String> actualModel(ModelRole role) {
        return Optional.ofNullable(models.get(role))
                .flatMap(state -> Optional.ofNullable(state.actualModel.get()));
    }

    private static State emptyState(ModelRole role) {
        return new State(new ModelConfiguration(
                role,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static final class State {

        private volatile ModelConfiguration configuration;
        private final AtomicReference<@Nullable String> actualModel = new AtomicReference<>();

        private State(ModelConfiguration configuration) {
            this.configuration = configuration;
        }

        private State withConfiguration(ModelConfiguration replacement) {
            configuration = replacement;
            return this;
        }

        private ModelSnapshot snapshot() {
            return new ModelSnapshot(configuration, Optional.ofNullable(actualModel.get()));
        }
    }
}
