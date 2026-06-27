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

/**
 * Supplies the application-under-test's OpenAPI description as text (YAML).
 *
 * <p>Because OpenAPI discovery is an explicit opt-in ({@code enabled=true}), an
 * implementation must <strong>fail fast</strong> rather than degrade silently: if the
 * spec cannot be obtained it throws, so the user learns the spec never reached the
 * model instead of silently running without it.
 */
public interface OpenApiSpecProvider {

    /**
     * @return the OpenAPI document as text
     * @throws RuntimeException if the spec cannot be obtained (unreachable, missing,
     *                          or empty)
     */
    String spec();
}
