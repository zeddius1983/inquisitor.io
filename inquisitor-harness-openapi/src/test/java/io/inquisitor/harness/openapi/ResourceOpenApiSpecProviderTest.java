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

package io.inquisitor.harness.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ResourceOpenApiSpecProviderTest {

    private final DefaultResourceLoader loader = new DefaultResourceLoader();

    @Test
    void loadsSpecFromClasspath() {
        val provider = new ResourceOpenApiSpecProvider(loader, "classpath:test-openapi.yaml");
        assertThat(provider.spec()).contains("openapi: 3.1.0").contains("/ping");
    }

    @Test
    void failsFastWhenMissing() {
        val provider = new ResourceOpenApiSpecProvider(loader, "classpath:does-not-exist.yaml");
        assertThatThrownBy(provider::spec)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }
}
