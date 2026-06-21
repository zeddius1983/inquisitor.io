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

package io.inquisitor.harness.tool;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.val;
import org.jspecify.annotations.Nullable;

/**
 * An allow-list of named entries a tool may use. A tool call may name an entry
 * explicitly; if it omits the name, the registry resolves it only when there is
 * exactly one entry — otherwise the call must say which.
 *
 * @param <T> the kind of entry (e.g. an HTTP target or a datasource)
 */
public abstract class NamedRegistry<T> {

    private final String kind;
    private final Map<String, T> entries = new ConcurrentHashMap<>();

    /**
     * @param kind singular noun used in messages, e.g. {@code "HTTP target"}
     */
    protected NamedRegistry(String kind) {
        this.kind = kind;
    }

    public void register(String name, T entry) {
        entries.put(name, entry);
    }

    /**
     * Resolves an entry by name, or the sole entry when {@code name} is omitted.
     *
     * @throws IllegalArgumentException if {@code name} is unknown, or omitted while
     *                                  several entries exist (the caller must specify)
     * @throws IllegalStateException    if omitted while no entries are registered
     */
    public T resolve(@Nullable String name) {
        if (name != null && !name.isBlank()) {
            val entry = entries.get(name.strip());
            if (entry == null) {
                throw new IllegalArgumentException(
                        "Unknown " + kind + " '" + name.strip() + "'. Registered: " + entries.keySet());
            }
            return entry;
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("No " + kind + " is registered.");
        }
        if (entries.size() > 1) {
            throw new IllegalArgumentException(
                    "Several " + kind + "s are registered; specify one of " + entries.keySet());
        }
        return entries.values().iterator().next();
    }

    public Set<String> names() {
        return Set.copyOf(entries.keySet());
    }
}
