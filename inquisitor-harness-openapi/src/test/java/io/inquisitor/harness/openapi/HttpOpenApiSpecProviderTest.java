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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import io.inquisitor.harness.tool.HttpTarget;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpOpenApiSpecProviderTest {

    private static final String SPEC = "openapi: 3.1.0\npaths: {}\n";

    @Test
    void fetchesSpecAndCachesIt() throws IOException {
        val hits = new AtomicInteger();
        val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/api-docs.yaml", exchange -> {
            hits.incrementAndGet();
            val bytes = SPEC.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        try {
            val provider = new HttpOpenApiSpecProvider(
                    registryFor(server.getAddress().getPort()), RestClient.builder(), "app", "/v3/api-docs.yaml");

            assertThat(provider.spec()).isEqualTo(SPEC);
            assertThat(provider.spec()).isEqualTo(SPEC); // served from cache
            assertThat(hits.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsFastWhenTheSpecIsUnreachable() {
        // Port 1 has nothing listening → connection refused.
        val provider = new HttpOpenApiSpecProvider(
                registryFor(1), RestClient.builder(), "app", "/v3/api-docs.yaml");

        assertThatThrownBy(provider::spec)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not fetch");
    }

    @Test
    void failsFastWhenTheTargetIsNotRegistered() {
        val provider = new HttpOpenApiSpecProvider(
                new HttpTargetRegistry(), RestClient.builder(), "app", "/v3/api-docs.yaml");

        assertThatThrownBy(provider::spec).isInstanceOf(RuntimeException.class);
    }

    private static HttpTargetRegistry registryFor(int port) {
        val registry = new HttpTargetRegistry();
        registry.register("app", HttpTarget.of("http://127.0.0.1:" + port));
        return registry;
    }
}
