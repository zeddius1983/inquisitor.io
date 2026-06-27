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

import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Live-fetches the OpenAPI spec from a registered HTTP target (the application under
 * test) the first time it is needed, then caches it for the rest of the run.
 *
 * <p>The fetch is lazy by design: the application's HTTP target is registered only
 * once the server is up (by the JUnit layer / standalone setup), so resolving it at
 * first use — during the first model call — avoids any startup hook. A failure to
 * resolve the target or fetch the document throws, per {@link OpenApiSpecProvider}'s
 * fail-fast contract.
 */
@Slf4j
public class HttpOpenApiSpecProvider implements OpenApiSpecProvider {

    private final HttpTargetRegistry registry;
    private final RestClient.Builder restClientBuilder;
    private final @Nullable String targetName;
    private final String path;

    private volatile @Nullable String cached;

    public HttpOpenApiSpecProvider(
            HttpTargetRegistry registry,
            RestClient.Builder restClientBuilder,
            @Nullable String targetName,
            String path) {
        this.registry = registry;
        this.restClientBuilder = restClientBuilder;
        this.targetName = targetName;
        this.path = path;
    }

    @Override
    public String spec() {
        var result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = fetch();
                    cached = result;
                }
            }
        }
        return result;
    }

    private String fetch() {
        // resolve(...) throws if the target is missing — the fail-fast we want when the
        // user enabled discovery but the app target was never registered.
        val target = registry.resolve(targetName);
        val where = target.baseUrl() + path;
        try {
            val body = restClientBuilder.clone()
                    .baseUrl(target.baseUrl())
                    .build()
                    .get()
                    .uri(path)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("OpenAPI spec fetched from " + where + " was empty.");
            }
            log.debug("Fetched OpenAPI spec from {} ({} chars)", where, body.length());
            return body;
        } catch (RestClientException e) {
            throw new IllegalStateException(
                    "Could not fetch the OpenAPI spec from " + where + " (OpenAPI discovery is enabled). "
                            + "Check that the app serves it, or set inquisitor.harness.openapi.location/path. "
                            + "Cause: " + e.getMessage(), e);
        }
    }
}
