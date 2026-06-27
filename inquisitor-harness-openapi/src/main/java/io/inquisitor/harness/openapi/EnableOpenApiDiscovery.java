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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables OpenAPI discovery for the annotated test class — the declarative equivalent
 * of setting {@code inquisitor.harness.openapi.enabled=true}. When present, the
 * harness fetches the application-under-test's OpenAPI description and injects it into
 * the model's system prompt so it can choose endpoints itself.
 *
 * <pre>{@code
 * @Harness(scenarioDir = "classpath:scenarios/")
 * @SpringBootTest(webEnvironment = RANDOM_PORT)
 * @EnableOpenApiDiscovery
 * class MyScenarioTest { ... }
 * }</pre>
 *
 * <p>Use {@code @EnableOpenApiDiscovery(enabled = false)} to turn discovery off for a
 * subclass that inherits an enabling annotation. The attribute maps directly onto the
 * {@code inquisitor.harness.openapi.enabled} property, so it works wherever Spring's
 * TestContext applies — no change to {@code @Harness} or the core harness.
 *
 * <p>This lives in the optional OpenAPI module: removing the plugin removes the
 * annotation with it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface EnableOpenApiDiscovery {

    /** Whether OpenAPI discovery is enabled. */
    boolean enabled() default true;
}
