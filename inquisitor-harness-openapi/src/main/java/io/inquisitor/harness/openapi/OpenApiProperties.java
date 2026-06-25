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

import io.inquisitor.harness.HarnessDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for OpenAPI discovery, bound from {@code inquisitor.harness.openapi}.
 *
 * <p>When {@code location} is set, the spec is loaded from there (a {@code classpath:},
 * {@code file:} or {@code http:} resource); otherwise it is live-fetched from the
 * registered {@code target}'s base URL + {@code path}.
 *
 * @param enabled  whether discovery is active (the autoconfiguration is conditional on
 *                 {@code true}); an explicit opt-in
 * @param location optional static spec location overriding live fetch
 * @param path     path appended to the target base URL for live fetch
 * @param target   name of the registered HTTP target to fetch the spec from
 */
@ConfigurationProperties("inquisitor.harness.openapi")
public record OpenApiProperties(
        boolean enabled,
        @Nullable String location,
        String path,
        String target) {

    public OpenApiProperties {
        path = (path == null || path.isBlank()) ? "/v3/api-docs.yaml" : path;
        target = (target == null || target.isBlank()) ? HarnessDefaults.APPLICATION : target;
    }
}
