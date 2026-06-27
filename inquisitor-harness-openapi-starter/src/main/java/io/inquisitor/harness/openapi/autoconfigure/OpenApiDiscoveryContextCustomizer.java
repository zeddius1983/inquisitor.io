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

import java.util.Map;

import lombok.val;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Applies the {@code inquisitor.harness.openapi.enabled} property derived from
 * {@link io.inquisitor.harness.openapi.EnableOpenApiDiscovery @EnableOpenApiDiscovery}
 * before the test's application context refreshes, so the OpenAPI autoconfiguration's
 * {@code @ConditionalOnProperty} sees it.
 *
 * <p>{@code equals}/{@code hashCode} include the flag so the TestContext cache keeps
 * separate contexts for discovery-on and discovery-off classes.
 */
final class OpenApiDiscoveryContextCustomizer implements ContextCustomizer {

    private static final String PROPERTY = "inquisitor.harness.openapi.enabled";
    private static final String SOURCE_NAME = "inquisitor-openapi-discovery";

    private final boolean enabled;

    OpenApiDiscoveryContextCustomizer(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        val source = new MapPropertySource(SOURCE_NAME, Map.of(PROPERTY, Boolean.toString(enabled)));
        context.getEnvironment().getPropertySources().addFirst(source);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof OpenApiDiscoveryContextCustomizer that && this.enabled == that.enabled);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(enabled);
    }
}
