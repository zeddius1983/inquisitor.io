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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.inquisitor.harness.openapi.EnableOpenApiDiscovery;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

class OpenApiDiscoveryContextCustomizerFactoryTest {

    private static final String PROPERTY = "inquisitor.harness.openapi.enabled";

    private final OpenApiDiscoveryContextCustomizerFactory factory = new OpenApiDiscoveryContextCustomizerFactory();

    @Test
    void noCustomizerWhenAnnotationAbsent() {
        assertThat(factory.createContextCustomizer(Plain.class, List.of())).isNull();
    }

    @Test
    void enabledByDefaultAppliesTrue() {
        val customizer = factory.createContextCustomizer(Enabled.class, List.of());

        assertThat(customizer).isNotNull();
        assertThat(propertyAfterCustomizing(customizer)).isEqualTo("true");
    }

    @Test
    void explicitlyDisabledAppliesFalse() {
        val customizer = factory.createContextCustomizer(Disabled.class, List.of());

        assertThat(customizer).isNotNull();
        assertThat(propertyAfterCustomizing(customizer)).isEqualTo("false");
    }

    @Test
    void enabledIsInheritedFromSuperclass() {
        val customizer = factory.createContextCustomizer(InheritsEnabled.class, List.of());

        assertThat(customizer).isNotNull();
        assertThat(propertyAfterCustomizing(customizer)).isEqualTo("true");
    }

    @Test
    void customizersWithSameFlagAreEqualForContextCaching() {
        val a = factory.createContextCustomizer(Enabled.class, List.of());
        val b = factory.createContextCustomizer(InheritsEnabled.class, List.of());
        val disabled = factory.createContextCustomizer(Disabled.class, List.of());

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(disabled);
    }

    private static String propertyAfterCustomizing(org.springframework.test.context.ContextCustomizer customizer) {
        try (val context = new GenericApplicationContext()) {
            customizer.customizeContext(context, null);
            return context.getEnvironment().getProperty(PROPERTY);
        }
    }

    static class Plain {}

    @EnableOpenApiDiscovery
    static class Enabled {}

    @EnableOpenApiDiscovery(enabled = false)
    static class Disabled {}

    static class InheritsEnabled extends Enabled {}
}
