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
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
 * @param logging     harness logging presentation settings
 */
@ConfigurationProperties("inquisitor.harness")
public record InquisitorHarnessProperties(
        Map<String, Target> targets,
        Map<String, Datasource> datasources,
        @DefaultValue Logging logging) {

    @ConstructorBinding
    public InquisitorHarnessProperties {
        targets = targets == null ? Map.of() : Map.copyOf(targets);
        datasources = datasources == null ? Map.of() : Map.copyOf(datasources);
        logging = logging == null ? new Logging(HarnessLoggingFormat.PLAIN) : logging;
    }

    /**
     * Creates plain-logging properties for standalone source compatibility.
     *
     * @param targets extra HTTP targets, keyed by name
     * @param datasources extra JDBC datasources, keyed by name
     */
    public InquisitorHarnessProperties(
            Map<String, Target> targets,
            Map<String, Datasource> datasources) {
        this(targets, datasources, new Logging(HarnessLoggingFormat.PLAIN));
    }

    /**
     * Harness logging settings.
     *
     * @param format presentation format used by the semantic harness loggers
     */
    public record Logging(
            @DefaultValue("plain") HarnessLoggingFormat format) {

        public Logging {
            format = format == null ? HarnessLoggingFormat.PLAIN : format;
        }
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
}
