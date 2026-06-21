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

import java.util.Locale;

import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Tool that lets the LLM make HTTP requests against a registered {@link HttpTarget}.
 *
 * <p>Error status codes (4xx/5xx) are returned to the model as data, not thrown —
 * a 404 or 422 is often the expected outcome of a step. Connection failures are
 * returned as a readable error string.
 */
public class HttpRequestTool {

    private final HttpTargetRegistry registry;

    public HttpRequestTool(HttpTargetRegistry registry) {
        this.registry = registry;
    }

    @Tool(description = "Make an HTTP request to a target application and return its response "
            + "(status code, content type and body). Use this to drive and inspect the application under test.")
    public String httpRequest(
            @ToolParam(required = false,
                    description = "target name; omit when there is only one target (the application under test)")
            @Nullable String target,
            @ToolParam(description = "HTTP method: GET, POST, PUT, PATCH or DELETE") String method,
            @ToolParam(description = "request path, e.g. /accounts or /accounts/42") String path,
            @ToolParam(required = false, description = "request body as JSON, or empty for none")
            @Nullable String body) {

        val httpTarget = registry.resolve(target);
        val client = RestClient.builder().baseUrl(httpTarget.baseUrl()).build();
        try {
            val spec = client.method(HttpMethod.valueOf(method.strip().toUpperCase(Locale.ROOT))).uri(path);
            httpTarget.defaultHeaders().forEach(spec::header);
            if (body != null && !body.isBlank()) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            val response = spec.retrieve()
                    .onStatus(status -> true, (request, ignored) -> { })
                    .toEntity(String.class);
            return formatResponse(response.getStatusCode(), response.getHeaders(), response.getBody());
        } catch (RestClientException e) {
            return "HTTP request failed: " + e.getMessage();
        }
    }

    private static String formatResponse(HttpStatusCode status, HttpHeaders headers, @Nullable String body) {
        return "HTTP %d%nContent-Type: %s%n%n%s".formatted(
                status.value(),
                headers.getContentType(),
                body == null ? "" : body);
    }
}
