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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Gates a scenario test on a configured LLM being available (local or remote). The
 * harness is model-driven, so its tests are slow and need a real model; this lets a
 * CI build that has no model stay green by <em>skipping</em> rather than failing.
 *
 * <p>Opt-in companion to {@link Harness @Harness} — keep them separate so {@code @Harness}
 * stays a pure capability marker and consumers are never silently skipped by merely
 * adding it. The idiomatic suite is:
 *
 * <pre>{@code
 * @Harness
 * @SpringBootTest(webEnvironment = RANDOM_PORT)
 * @RequiresLlm
 * class MyScenarioSuiteTest {
 *     @Scenario void transferBetweenAccounts() {}
 * }
 * }</pre>
 *
 * <p><strong>Enablement</strong> is resolved by {@link RequiresLlmCondition} in this
 * order:
 * <ol>
 *   <li>the {@value RequiresLlmCondition#ENABLED_ENV} environment variable, if set,
 *       is authoritative (enabled when {@code true});</li>
 *   <li>otherwise the {@value RequiresLlmCondition#ENABLED_PROPERTY} JUnit
 *       configuration parameter (resolvable from a {@code -D} system property or a
 *       {@code junit-platform.properties} file on the test classpath);</li>
 *   <li>otherwise the test is disabled (off by default, so CI stays green with no
 *       model).</li>
 * </ol>
 * The condition runs before the Spring {@code TestContext} is built, so it cannot read
 * {@code application.yml}; the configuration parameter is the file-based equivalent
 * available at that point.
 *
 * <p>{@link Inherited @Inherited} is load-bearing: JUnit's condition lookup does not
 * walk to a superclass for a plain annotation, so a non-inherited gate on an abstract
 * base would not gate its subclasses (whereas {@code @SpringBootTest}, which Spring
 * <em>does</em> inherit, would still make them run). Marking this {@code @Inherited}
 * lets a suite hierarchy declare the gate once on its base.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(RequiresLlmCondition.class)
public @interface RequiresLlm {}
