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

package io.inquisitor.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.val;
import org.junit.jupiter.api.Test;

/** Exercises the shared {@link NamedRegistry} resolution rules via one subclass. */
class HttpTargetRegistryTest {

    @Test
    void resolvesByExplicitName() {
        val registry = new HttpTargetRegistry();
        registry.register("app", HttpTarget.of("http://app"));
        registry.register("mock", HttpTarget.of("http://mock"));

        assertThat(registry.resolve("mock").baseUrl()).isEqualTo("http://mock");
    }

    @Test
    void usesSoleEntryWhenNameOmitted() {
        val registry = new HttpTargetRegistry();
        registry.register("app", HttpTarget.of("http://app"));

        assertThat(registry.resolve(null).baseUrl()).isEqualTo("http://app");
        assertThat(registry.resolve("  ").baseUrl()).isEqualTo("http://app");
    }

    @Test
    void failsWhenNameOmittedAndSeveralExist() {
        val registry = new HttpTargetRegistry();
        registry.register("app", HttpTarget.of("http://app"));
        registry.register("mock", HttpTarget.of("http://mock"));

        assertThatThrownBy(() -> registry.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Several HTTP targets");
    }

    @Test
    void failsWhenNameUnknown() {
        val registry = new HttpTargetRegistry();
        registry.register("app", HttpTarget.of("http://app"));

        assertThatThrownBy(() -> registry.resolve("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown HTTP target 'nope'");
    }

    @Test
    void failsWhenEmptyAndNameOmitted() {
        assertThatThrownBy(() -> new HttpTargetRegistry().resolve(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No HTTP target");
    }
}
