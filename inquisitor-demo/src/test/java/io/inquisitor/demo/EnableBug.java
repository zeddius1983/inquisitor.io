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

package io.inquisitor.demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.inquisitor.demo.service.Bug;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Enables a seeded {@link Bug} in the {@code @Primary}
 * {@link io.inquisitor.demo.service.AccountServiceRouter} for the duration of a
 * {@link io.inquisitor.harness.junit.Scenario @Scenario} method, and resets it
 * afterwards. Pair it with {@code @Scenario(expect = FAIL)} to assert the oracle
 * catches the fault.
 *
 * <p>Carries the {@link BugInjectionExtension} as a meta-annotation, so simply
 * putting {@code @EnableBug(...)} on a method wires the bug toggling for that method
 * — no class-level {@code @ExtendWith} needed. See {@code tasks/task-07-fault-detection.md}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(BugInjectionExtension.class)
public @interface EnableBug {

    /** The bug to enable while the annotated scenario runs. */
    Bug value();
}
