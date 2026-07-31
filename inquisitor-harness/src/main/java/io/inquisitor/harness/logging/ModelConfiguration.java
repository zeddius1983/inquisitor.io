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

import java.util.Optional;

/** Non-sensitive configured model and connection settings for one harness role. */
public record ModelConfiguration(
        ModelRole role,
        Optional<String> configuredModel,
        Optional<String> baseUrl,
        Optional<Double> temperature,
        Optional<Double> topP,
        Optional<Integer> maxTokens,
        Optional<Integer> maxCompletionTokens,
        Optional<String> reasoningEffort) {

    public ModelConfiguration {
        configuredModel = normalized(configuredModel);
        baseUrl = normalized(baseUrl);
        reasoningEffort = normalized(reasoningEffort);
    }

    private static Optional<String> normalized(Optional<String> value) {
        return value.map(String::strip).filter(candidate -> !candidate.isBlank());
    }
}
