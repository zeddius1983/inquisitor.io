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
 * <p>The markdown file is {@link #value()} when given, otherwise it is derived from
 * the method name: camelCase becomes kebab-case plus {@code .md}, resolved against
 * the class's {@link Harness#scenarioDir()} (e.g. {@code transferBetweenAccounts()}
 * → {@code classpath:scenarios/transfer-between-accounts.md}).
 *
 * <p>{@link #expect()} is normally {@link Expect#PASS}. Set it to {@link Expect#FAIL}
 * for fault detection: the scenario is then expected to fail at some step (a failing
 * step is the success condition), which is how a correct scenario run against a
 * deliberately defective build asserts the oracle caught the fault.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ScenarioTemplateProvider.class)
public @interface Scenario {

    /** Explicit classpath location of the scenario markdown; derived from the method name when blank. */
    String value() default "";

    /** Whether the scenario is expected to pass (default) or to fail at some step. */
    Expect expect() default Expect.PASS;
}
