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

import java.util.Map;

/**
 * A named HTTP endpoint the harness may call: a base URL plus headers sent on
 * every request to it (e.g. auth).
 */
public record HttpTarget(String baseUrl, Map<String, String> defaultHeaders) {

    public HttpTarget {
        defaultHeaders = Map.copyOf(defaultHeaders);
    }

    /** A target with no default headers. */
    public static HttpTarget of(String baseUrl) {
        return new HttpTarget(baseUrl, Map.of());
    }
}
