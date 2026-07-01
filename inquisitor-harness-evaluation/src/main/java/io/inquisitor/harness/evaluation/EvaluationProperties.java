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

package io.inquisitor.harness.evaluation;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Step-evaluation settings, bound from {@code inquisitor.harness.evaluation}. When
 * {@code enabled}, the evaluation starter wraps the actor runner so a separate judge
 * model scores each verdict against the real tool trace.
 *
 * <p>The judge should be a <em>different</em> model from the actor — a self-judge shares
 * its own failure modes. {@code baseUrl}/{@code apiKey} default to the actor model's
 * {@code spring.ai.openai.*} settings when omitted, in which case only {@code model} differs.
 *
 * @param enabled whether to run step evaluation (the autoconfiguration is conditional on
 *                {@code true}); an explicit opt-in
 * @param model   the judge model name; required when {@code enabled}
 * @param baseUrl the judge model's OpenAI-compatible base URL, or {@code null} to reuse the actor's
 * @param apiKey  the judge model's API key, or {@code null} to reuse the actor's
 */
@ConfigurationProperties("inquisitor.harness.evaluation")
public record EvaluationProperties(
        boolean enabled,
        @Nullable String model,
        @Nullable String baseUrl,
        @Nullable String apiKey) {
}
