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

import java.util.List;

import io.inquisitor.harness.openapi.EnableOpenApiDiscovery;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Binds {@link EnableOpenApiDiscovery @EnableOpenApiDiscovery} on a test class to the
 * {@code inquisitor.harness.openapi.enabled} property, so the annotation is equivalent
 * to setting that property. Registered via {@code META-INF/spring.factories} and
 * discovered by the Spring TestContext framework.
 */
public class OpenApiDiscoveryContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public @Nullable ContextCustomizer createContextCustomizer(
            Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {

        val annotation = TestContextAnnotationUtils.findMergedAnnotation(testClass, EnableOpenApiDiscovery.class);
        return annotation == null ? null : new OpenApiDiscoveryContextCustomizer(annotation.enabled());
    }
}
