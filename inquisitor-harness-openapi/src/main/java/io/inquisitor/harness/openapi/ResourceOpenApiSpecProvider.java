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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ResourceLoader;

/**
 * Loads the OpenAPI spec from a static location (a {@code classpath:}, {@code file:}
 * or {@code http:} resource), once, and caches it. A missing or unreadable resource
 * throws, per {@link OpenApiSpecProvider}'s fail-fast contract.
 */
public class ResourceOpenApiSpecProvider implements OpenApiSpecProvider {

    private final ResourceLoader resourceLoader;
    private final String location;

    private volatile @Nullable String cached;

    public ResourceOpenApiSpecProvider(ResourceLoader resourceLoader, String location) {
        this.resourceLoader = resourceLoader;
        this.location = location;
    }

    @Override
    public String spec() {
        var result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = load();
                    cached = result;
                }
            }
        }
        return result;
    }

    private String load() {
        val resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "OpenAPI spec not found at " + location + " (OpenAPI discovery is enabled).");
        }
        try {
            val body = resource.getContentAsString(StandardCharsets.UTF_8);
            if (body.isBlank()) {
                throw new IllegalStateException("OpenAPI spec at " + location + " was empty.");
            }
            return body;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read the OpenAPI spec at " + location + ": " + e.getMessage(), e);
        }
    }
}
