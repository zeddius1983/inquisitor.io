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

package io.inquisitor.harness.config;

/**
 * How the actor is asked to shape its verdict as JSON.
 *
 * <p>A weak or heavily quantized model can answer a step in prose, which fails to
 * deserialize and is degraded to a synthetic FAIL — a run that says nothing about the
 * application under test. This selects how hard the harness pushes for well-formed
 * JSON.
 */
public enum StructuredOutputMode {

    /**
     * Append the schema to the prompt as format instructions and hope the model
     * complies (Spring AI's default). Portable across every provider; a model is free
     * to ignore it.
     */
    PROMPT,

    /**
     * Send the verdict schema to the provider as a native structured-output request, so
     * the schema is enforced while decoding rather than merely requested.
     *
     * <p>Requires a provider that implements it — llama.cpp converts the schema to a
     * GBNF grammar and masks any token that would break it, as do the hosted OpenAI-style
     * Structured Outputs APIs. Providers that ignore the field silently fall back to
     * {@link #PROMPT} behaviour.
     */
    NATIVE
}
