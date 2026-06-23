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
        server.createContext("/headers", exchange -> {
            val requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            val contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "X-Request-Id=" + requestId + " Content-Type=" + contentType);
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
        val response = tool.httpRequest(null, "GET", "/ping", null, null);

        assertThat(response).contains("HTTP 200").contains("\"msg\":\"pong\"");
    }

    @Test
    void capturesErrorStatusInsteadOfThrowing() {
        val response = tool.httpRequest(null, "GET", "/missing", null, null);

        assertThat(response).contains("HTTP 404").contains("not found");
    }

    @Test
    void sendsRequestBody() {
        val response = tool.httpRequest(null, "POST", "/echo", "{\"a\":1}", null);

        assertThat(response).contains("HTTP 201").contains("\"a\":1");
    }

    @Test
    void sendsCustomHeadersAndContentType() {
        val response = tool.httpRequest(null, "POST", "/headers", "plain text body",
                "X-Request-Id: req-001\nContent-Type: text/plain");

        assertThat(response).contains("HTTP 200")
                .contains("X-Request-Id=req-001")
                .contains("Content-Type=text/plain");
    }

    @Test
    void defaultsToJsonContentTypeWhenBodyHasNoContentTypeHeader() {
        val response = tool.httpRequest(null, "POST", "/headers", "{\"a\":1}", "X-Request-Id: req-002");

        assertThat(response).contains("HTTP 200")
                .contains("X-Request-Id=req-002")
                .contains("Content-Type=application/json");
    }

    @Test
    void rejectsUnknownTarget() {
        assertThatThrownBy(() -> tool.httpRequest("nope", "GET", "/ping", null, null))
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
