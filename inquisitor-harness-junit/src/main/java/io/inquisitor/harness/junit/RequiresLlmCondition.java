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

import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a {@link RequiresLlm @RequiresLlm} test runs. An environment
 * variable takes precedence (handy for a shell or CI export); when it is absent a
 * JUnit configuration parameter is the fallback. The parameter resolves from a
 * {@code -D} system property or a {@code junit-platform.properties} file on the test
 * classpath — the file-based "property" equivalent that is available here, before the
 * Spring {@code TestContext} (and thus {@code application.yml}) exists.
 */
public class RequiresLlmCondition implements ExecutionCondition {

    /** Environment variable that enables the gated tests when {@code true}. */
    public static final String ENABLED_ENV = "INQUISITOR_LLM_IT";

    /** JUnit configuration parameter that enables the gated tests when {@code true}. */
    public static final String ENABLED_PROPERTY = "inquisitor.harness.llm.enabled";

    private static final ConditionEvaluationResult DISABLED = ConditionEvaluationResult.disabled(
            "No LLM configured: set the " + ENABLED_ENV + " environment variable or the "
                    + ENABLED_PROPERTY + " configuration parameter to true to run scenario tests.");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return resolve(System.getenv(ENABLED_ENV), context.getConfigurationParameter(ENABLED_PROPERTY));
    }

    /**
     * Pure resolution of the gate from its two inputs, split out from
     * {@link #evaluateExecutionCondition} so it is testable without touching the
     * ambient environment. The environment variable, when present, is authoritative;
     * otherwise the configuration parameter decides; absent both, the test is disabled.
     */
    static ConditionEvaluationResult resolve(@Nullable String env, Optional<String> property) {
        if (env != null) {
            return resultFor(env, ENABLED_ENV + " environment variable");
        }
        return property
                .map(value -> resultFor(value, ENABLED_PROPERTY + " configuration parameter"))
                .orElse(DISABLED);
    }

    private static ConditionEvaluationResult resultFor(String value, String source) {
        return Boolean.parseBoolean(value.trim())
                ? ConditionEvaluationResult.enabled(source + " is true")
                : ConditionEvaluationResult.disabled(source + " is \"" + value + "\", not true");
    }
}
