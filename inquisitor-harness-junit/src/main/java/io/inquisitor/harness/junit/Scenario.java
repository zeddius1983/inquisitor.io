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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs one markdown scenario as a test. Put it on an empty {@code void} method of a
 * {@link Harness @Harness} class; each {@code @Scenario} method is one scenario.
 *
 * <p>The method is a {@link TestTemplate}: every {@code ## Step} in the scenario is
 * reported as its own sub-test under the method (like the invocations of a
 * parameterized test), the steps share one conversation, and the first failing step
 * fails with the rest skipped.
 *
 * <p>The scenario, not the step, is the test as far as per-test lifecycle goes: these
 * run once before the first step and once after the last, not around every step —
 * <ul>
 *   <li>Spring's {@code @Sql} fixtures ({@code BEFORE_TEST_METHOD} /
 *       {@code AFTER_TEST_METHOD} scripts);</li>
 *   <li>{@code @DirtiesContext} method modes (and the class's
 *       {@code BEFORE_EACH_TEST_METHOD} / {@code AFTER_EACH_TEST_METHOD} modes);</li>
 *   <li>the reset of {@code @MockitoBean} / {@code @MockitoSpyBean} mocks;</li>
 *   <li>{@code @RecordApplicationEvents} (the events of every step are kept) and the
 *       test-method events of {@code @BeforeTestMethod} / {@code @AfterTestMethod};</li>
 *   <li>the test class's JUnit {@code @BeforeEach} / {@code @AfterEach} methods.</li>
 * </ul>
 * A test-managed {@code @Transactional} is rejected: the application under test serves
 * requests on its own threads and could never see the test's uncommitted data. Seed
 * with {@code @Sql} and clean up with an {@code AFTER_TEST_METHOD} script instead.
 *
 * <p>This is specific to {@code @Scenario}: {@code @Test}, {@code @ParameterizedTest}
 * and other templates in the same class keep Spring's and JUnit's usual
 * per-invocation behaviour.
 *
 * <p>The markdown file is {@link #value()} when given, otherwise it is derived from
 * the method name: camelCase becomes kebab-case plus {@code .md}, resolved against
 * the class's {@link Harness#scenarioDir()} (e.g. {@code transferBetweenAccounts()}
 * → {@code classpath:scenarios/transfer-between-accounts.md}).
 *
 * <p>{@link #expect()} is normally {@link Expect#PASS}. Set it to {@link Expect#FAIL}
 * for fault detection: the scenario is then expected to fail at some step (a failing
 * step is the success condition), which is how a correct scenario run against a
 * deliberately defective build asserts the oracle caught the fault.
 *
 * <p>Every {@code @Scenario} carries the meta-annotated JUnit tag {@code inquisitor},
 * so scenario methods are selectable as a group with no annotation on the test class —
 * that is how the Gradle plugin's {@code evaluate} task (default
 * {@code includeTags("inquisitor")}) finds them.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@Tag("inquisitor")
@ExtendWith(ScenarioTemplateProvider.class)
public @interface Scenario {

    /** Explicit classpath location of the scenario markdown; derived from the method name when blank. */
    String value() default "";

    /** Whether the scenario is expected to pass (default) or to fail at some step. */
    Expect expect() default Expect.PASS;
}
