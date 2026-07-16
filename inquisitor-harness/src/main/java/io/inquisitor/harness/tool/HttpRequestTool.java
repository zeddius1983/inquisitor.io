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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            @ToolParam(description = "request path with optional query string, written raw and "
                    + "unencoded — the tool URL-encodes it. E.g. /accounts/42 or /accounts?owner=Jackie C. "
                    + "Never percent-encode values yourself (no %20): it would be encoded twice.")
            String path,
            @ToolParam(required = false, description = "request body, or empty for none")
            @Nullable String body,
            @ToolParam(required = false,
                    description = "extra request headers, one per line as \"Name: Value\", e.g. "
                            + "\"X-Request-Id: req-001\". Add a \"Content-Type: ...\" line for a non-JSON "
                            + "body; when a body is sent without one, application/json is assumed.")
            @Nullable String headers) {

        log.debug("httpRequest <- target={}, method={}, path={}, headers={}, body={}",
                target, method, path, headers, body);
        val httpTarget = registry.resolve(target);
        val client = RestClient.builder().baseUrl(httpTarget.baseUrl()).build();
        try {
            val spec = client.method(HttpMethod.valueOf(method.strip().toUpperCase(Locale.ROOT))).uri(path);

            // Per-target default headers first, then the per-request ones (which win on conflict).
            val merged = new LinkedHashMap<String, String>(httpTarget.defaultHeaders());
            parseHeaders(headers, merged);

            // Content-Type is applied alongside the body (case-insensitive lookup); every other
            // header is set directly on the request.
            String contentType = null;
            for (val header : merged.entrySet()) {
                if (header.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
                    contentType = header.getValue();
                } else {
                    spec.header(header.getKey(), header.getValue());
                }
            }

            if (body != null && !body.isBlank()) {
                val mediaType = contentType != null && !contentType.isBlank()
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_JSON;
                spec.contentType(mediaType).body(body);
            } else if (contentType != null && !contentType.isBlank()) {
                spec.header(HttpHeaders.CONTENT_TYPE, contentType);
            }

            val response = spec.retrieve()
                    .onStatus(status -> true, (request, ignored) -> { })
                    .toEntity(String.class);
            val result = formatResponse(response.getStatusCode(), response.getHeaders(), response.getBody());
            log.debug("httpRequest -> {}", result);
            return result;
        } catch (RestClientException e) {
            val error = "HTTP request failed: " + e.getMessage();
            log.debug("httpRequest -> {}", error);
            return error;
        }
    }

    /** Parses {@code "Name: Value"} lines into {@code target}; blanks are ignored, later lines win. */
    private static void parseHeaders(@Nullable String headers, Map<String, String> target) {
        if (headers == null || headers.isBlank()) {
            return;
        }
        for (val line : headers.lines().toList()) {
            val trimmed = line.strip();
            val colon = trimmed.indexOf(':');
            if (colon > 0) {
                target.put(trimmed.substring(0, colon).strip(), trimmed.substring(colon + 1).strip());
            }
        }
    }

    private static String formatResponse(HttpStatusCode status, HttpHeaders headers, @Nullable String body) {
        return "HTTP %d%nContent-Type: %s%n%n%s".formatted(
                status.value(),
                headers.getContentType(),
                body == null ? "" : body);
    }
}
