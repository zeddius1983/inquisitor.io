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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class as driven by the Inquisitor harness.
 *
 * <p>Put it on a {@code @SpringBootTest} class whose methods are annotated with
 * {@link Scenario @Scenario}. It registers the application's HTTP target from the
 * (random) server port before each test, so scenarios reach the running app
 * without any per-class setup:
 *
 * <pre>{@code
 * @Harness
 * @SpringBootTest(webEnvironment = RANDOM_PORT)
 * class ScenarioSuiteTest {
 *
 *     @Scenario void transferBetweenAccounts() {}
 *     @Scenario void accountNotFound() {}
 * }
 * }</pre>
 *
 * <p>{@link #scenarioDir()} is the base classpath directory used to resolve a
 * {@code @Scenario} method to its markdown file by name (see {@link Scenario}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(HarnessExtension.class)
public @interface Harness {

    /** Base classpath directory for resolving {@link Scenario @Scenario} files by method name. */
    String scenarioDir() default "classpath:scenarios/";
}
