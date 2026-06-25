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

package io.inquisitor.harness.openapi.autoconfigure;

import io.inquisitor.harness.openapi.OpenApiSpecProvider;
import io.inquisitor.harness.openapi.HttpOpenApiSpecProvider;
import io.inquisitor.harness.openapi.OpenApiAdvisor;
import io.inquisitor.harness.openapi.OpenApiProperties;
import io.inquisitor.harness.openapi.ResourceOpenApiSpecProvider;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.val;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.client.RestClient;

/**
 * Optional OpenAPI-discovery autoconfiguration: contributes an {@link OpenApiAdvisor}
 * that the harness ChatClient picks up (it collects {@code Advisor} beans), injecting
 * the application's OpenAPI spec into the system prompt.
 *
 * <p>Active only when {@code inquisitor.harness.openapi.enabled=true} — an explicit
 * opt-in. Ordered before the harness autoconfiguration so the advisor bean is in place
 * when the ChatClient is built.
 */
@AutoConfiguration(beforeName = "io.inquisitor.harness.autoconfigure.InquisitorHarnessAutoConfiguration")
@ConditionalOnClass(BaseAdvisor.class)
@ConditionalOnProperty(prefix = "inquisitor.harness.openapi", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OpenApiProperties.class)
public class InquisitorOpenApiAutoConfiguration {

    /**
     * A static {@code location} selects the resource-based provider; otherwise the spec
     * is live-fetched from the registered target.
     */
    @Bean
    @ConditionalOnMissingBean
    OpenApiSpecProvider inquisitorOpenApiSpecProvider(
            OpenApiProperties properties,
            HttpTargetRegistry targetRegistry,
            ResourceLoader resourceLoader) {

        val location = properties.location();
        if (location != null && !location.isBlank()) {
            return new ResourceOpenApiSpecProvider(resourceLoader, location);
        }
        return new HttpOpenApiSpecProvider(
                targetRegistry, RestClient.builder(), properties.target(), properties.path());
    }

    @Bean
    @ConditionalOnMissingBean
    OpenApiAdvisor inquisitorOpenApiAdvisor(OpenApiSpecProvider apiSpecProvider) {
        return new OpenApiAdvisor(apiSpecProvider);
    }
}
