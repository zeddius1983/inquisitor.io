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

package io.inquisitor.harness.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import lombok.val;
import org.jspecify.annotations.Nullable;

/** Presentation seam for HTTP tool request and response diagnostics. */
public interface HttpRequestLogger {

    /** Invoked immediately before the request is sent. */
    void requestStarted(Request request);

    /** Invoked after an HTTP response, including an error-status response. */
    void requestCompleted(Request request, Response response);

    /** Invoked when the HTTP client cannot obtain a response. */
    void requestFailed(Request request, String error);

    /** The effective request data made available to loggers. */
    record Request(
            String target,
            String method,
            String path,
            Map<String, String> headers,
            @Nullable String body) {

        public Request {
            val safeHeaders = new LinkedHashMap<String, String>();
            for (val header : headers.entrySet()) {
                safeHeaders.put(header.getKey(), isSensitive(header.getKey())
                        ? "[REDACTED]"
                        : header.getValue());
            }
            headers = Collections.unmodifiableMap(safeHeaders);
        }
    }

    /** The response data made available to loggers. */
    record Response(
            int status,
            @Nullable String contentType,
            @Nullable String body) {
    }

    private static boolean isSensitive(String name) {
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "authorization", "proxy-authorization", "cookie", "set-cookie",
                    "x-api-key", "api-key" -> true;
            default -> normalized.endsWith("-token") || normalized.endsWith("-secret");
        };
    }
}
