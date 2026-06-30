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

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Extra HTTP targets and datasources the harness exposes to the model, beyond the
 * application under test (which the starter registers automatically). Bound from
 * {@code inquisitor.harness.*}.
 *
 * <p>A scenario only needs to name a target/datasource when more than one is
 * registered; with a single entry the name may be omitted.
 *
 * @param targets     extra HTTP targets, keyed by the name a scenario refers to
 * @param datasources extra JDBC datasources, keyed by the name a scenario refers to
 * @param evaluation  credibility-evaluation settings (off by default)
 */
@ConfigurationProperties("inquisitor.harness")
public record InquisitorHarnessProperties(
        Map<String, Target> targets,
        Map<String, Datasource> datasources,
        Evaluation evaluation) {

    public InquisitorHarnessProperties {
        targets = targets == null ? Map.of() : Map.copyOf(targets);
        datasources = datasources == null ? Map.of() : Map.copyOf(datasources);
        evaluation = evaluation == null ? new Evaluation(false, null, null, null) : evaluation;
    }

    /**
     * An additional HTTP endpoint.
     *
     * @param baseUrl        base URL prepended to request paths
     * @param defaultHeaders headers sent on every request to this target (e.g. auth)
     */
    public record Target(String baseUrl, Map<String, String> defaultHeaders) {
        public Target {
            defaultHeaders = defaultHeaders == null ? Map.of() : Map.copyOf(defaultHeaders);
        }
    }

    /**
     * An additional JDBC datasource.
     *
     * @param url             the JDBC URL
     * @param username        the database user
     * @param password        the database password
     * @param driverClassName the JDBC driver class, or {@code null} to let the driver
     *                        be inferred from the URL
     */
    public record Datasource(
            String url,
            String username,
            String password,
            @Nullable String driverClassName) {
    }

    /**
     * Step-evaluation settings. When {@code enabled}, the harness wraps the actor runner
     * so a separate judge model scores each verdict against the real tool trace.
     *
     * <p>The judge should be a <em>different</em> model from the actor — a self-judge
     * shares its own failure modes. {@code baseUrl}/{@code apiKey} default to the actor
     * model's when omitted, in which case only {@code model} differs.
     *
     * @param enabled whether to run step evaluation
     * @param model   the judge model name; required when {@code enabled}
     * @param baseUrl the judge model's OpenAI-compatible base URL, or {@code null} to reuse the actor's
     * @param apiKey  the judge model's API key, or {@code null} to reuse the actor's
     */
    public record Evaluation(
            boolean enabled,
            @Nullable String model,
            @Nullable String baseUrl,
            @Nullable String apiKey) {
    }
}
