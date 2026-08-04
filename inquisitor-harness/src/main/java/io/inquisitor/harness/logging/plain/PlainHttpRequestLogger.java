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

package io.inquisitor.harness.logging.plain;

import io.inquisitor.harness.logging.HttpRequestLogger;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/** Conventional HTTP tool diagnostics. */
@Slf4j
public class PlainHttpRequestLogger implements HttpRequestLogger {

    @Override
    public void requestStarted(Request request) {
        log.debug("httpRequest <- target={}, method={}, path={}, headers={}, body={}",
                request.target(), request.method(), request.path(),
                request.headers(), request.body());
    }

    @Override
    public void requestCompleted(Request request, Response response) {
        log.debug("httpRequest -> HTTP {}\nContent-Type: {}\n\n{}",
                response.status(), response.contentType(), body(response.body()));
    }

    @Override
    public void requestFailed(Request request, String error) {
        log.debug("httpRequest -> {}", error);
    }

    private static String body(@Nullable String body) {
        return body == null ? "" : body;
    }
}
