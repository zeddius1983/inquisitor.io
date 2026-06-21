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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpRequestToolTest {

    private static HttpServer server;
    private static HttpRequestTool tool;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ping", exchange -> respond(exchange, 200, "{\"msg\":\"pong\"}"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "{\"error\":\"not found\"}"));
        server.createContext("/echo", exchange -> {
            val received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, 201, received);
        });
        server.start();

        val registry = new HttpTargetRegistry();
        registry.register("app", HttpTarget.of("http://127.0.0.1:" + server.getAddress().getPort()));
        tool = new HttpRequestTool(registry);
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsStatusAndBodyForSuccess() {
        val response = tool.httpRequest(null, "GET", "/ping", null);

        assertThat(response).contains("HTTP 200").contains("\"msg\":\"pong\"");
    }

    @Test
    void capturesErrorStatusInsteadOfThrowing() {
        val response = tool.httpRequest(null, "GET", "/missing", null);

        assertThat(response).contains("HTTP 404").contains("not found");
    }

    @Test
    void sendsRequestBody() {
        val response = tool.httpRequest(null, "POST", "/echo", "{\"a\":1}");

        assertThat(response).contains("HTTP 201").contains("\"a\":1");
    }

    @Test
    void rejectsUnknownTarget() {
        assertThatThrownBy(() -> tool.httpRequest("nope", "GET", "/ping", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown HTTP target 'nope'");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        val bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (val out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
