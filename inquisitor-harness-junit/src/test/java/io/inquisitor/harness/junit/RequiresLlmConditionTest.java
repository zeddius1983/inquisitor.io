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

package io.inquisitor.harness.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Verifies the enablement precedence of {@link RequiresLlm @RequiresLlm}: environment
 * variable over configuration parameter, off by default. Exercises the pure
 * {@link RequiresLlmCondition#resolve} so the outcome does not depend on the ambient
 * {@code INQUISITOR_LLM_IT} of whoever runs the build.
 */
class RequiresLlmConditionTest {

    @Test
    void disabledWhenNeitherSet() {
        assertThat(RequiresLlmCondition.resolve(null, Optional.empty()).isDisabled()).isTrue();
    }

    @Test
    void configParameterTrueEnables() {
        assertThat(RequiresLlmCondition.resolve(null, Optional.of("true")).isDisabled()).isFalse();
    }

    @Test
    void configParameterIsCaseInsensitive() {
        assertThat(RequiresLlmCondition.resolve(null, Optional.of("TRUE")).isDisabled()).isFalse();
    }

    @Test
    void configParameterFalseDisables() {
        assertThat(RequiresLlmCondition.resolve(null, Optional.of("false")).isDisabled()).isTrue();
    }

    @Test
    void environmentVariableTrueEnables() {
        assertThat(RequiresLlmCondition.resolve("true", Optional.empty()).isDisabled()).isFalse();
    }

    @Test
    void environmentVariableWinsOverConfigParameter() {
        // env present and false is authoritative even when the property says true.
        assertThat(RequiresLlmCondition.resolve("false", Optional.of("true")).isDisabled()).isTrue();
        assertThat(RequiresLlmCondition.resolve("true", Optional.of("false")).isDisabled()).isFalse();
    }
}
