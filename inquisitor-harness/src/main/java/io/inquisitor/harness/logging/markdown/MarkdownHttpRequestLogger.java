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

package io.inquisitor.harness.logging.markdown;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import io.inquisitor.harness.logging.HttpRequestLogger;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Marker-tagged Markdown HTTP tool diagnostics. */
@Slf4j
public class MarkdownHttpRequestLogger implements HttpRequestLogger {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Override
    public void requestStarted(Request request) {
        val contentType = contentType(request.headers());
        val event = MarkdownLogSupport.event(breadcrumb(request));
        if (!request.headers().isEmpty()) {
            event.section("Headers", MarkdownLogSupport.codeBlock(
                    "http", headers(request.headers())));
        }
        if (!isMissing(request.body())) {
            event.section("Body", MarkdownLogSupport.codeBlock(
                    language(request.body(), contentType), body(request.body(), contentType)));
        }
        log.info(MarkdownLogSupport.marker(), event.message());
    }

    @Override
    public void requestCompleted(Request request, Response response) {
        val event = MarkdownLogSupport.event(
                breadcrumb(request, "HTTP " + response.status()));
        if (!isMissing(response.body())) {
            event.section("Response", MarkdownLogSupport.codeBlock(
                    language(response.body(), response.contentType()),
                    body(response.body(), response.contentType())));
        }
        log.info(MarkdownLogSupport.marker(), event.message());
    }

    @Override
    public void requestFailed(Request request, String error) {
        val breadcrumb = breadcrumb(request, "ERROR");
        val event = MarkdownLogSupport.event(breadcrumb)
                .content(MarkdownStepLogSupport.blockquote(error, breadcrumb.length()));
        log.info(MarkdownLogSupport.marker(), event.message());
    }

    private static String breadcrumb(Request request, String... trailingSegments) {
        return MarkdownBreadcrumbs.http(
                request.target(), request.method(), request.path(), trailingSegments);
    }

    private static String headers(Map<String, String> headers) {
        return headers.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static @Nullable String contentType(Map<String, String> headers) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("Content-Type"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String body(@Nullable String body, @Nullable String contentType) {
        if (body == null || body.isBlank()) {
            return "";
        }
        if (!isJson(contentType) && !looksLikeJson(body)) {
            return body;
        }
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(body));
        }
        catch (JacksonException exception) {
            return body;
        }
    }

    private static String language(@Nullable String body, @Nullable String contentType) {
        return isJson(contentType) || looksLikeJson(body) ? "json" : "text";
    }

    private static boolean isJson(@Nullable String contentType) {
        return contentType != null
                && contentType.toLowerCase(Locale.ROOT).contains("json");
    }

    private static boolean looksLikeJson(@Nullable String body) {
        if (body == null) {
            return false;
        }
        val stripped = body.stripLeading();
        return stripped.startsWith("{") || stripped.startsWith("[");
    }

    private static boolean isMissing(@Nullable String value) {
        return value == null || value.isBlank();
    }

}
