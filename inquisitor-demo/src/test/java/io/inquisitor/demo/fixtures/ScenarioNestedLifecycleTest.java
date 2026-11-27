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

package io.inquisitor.demo.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import io.inquisitor.harness.junit.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;

/**
 * Pins that the enclosing class's {@link BeforeEach @BeforeEach} / {@link AfterEach @AfterEach}
 * bracket a {@link Scenario @Scenario} declared in a {@link Nested @Nested} class, on the
 * enclosing test instance of the first step — so tear-down sees what set-up initialized.
 */
@FakeActorTest
class ScenarioNestedLifecycleTest {

    static final AtomicInteger AFTER_EACH = new AtomicInteger();

    /** Initialized by the enclosing set-up; every step has its own instances. */
    private String resource;

    @BeforeEach
    void setUp() {
        resource = "opened";
    }

    @AfterEach
    void tearDown() {
        assertThat(resource).as("field initialized by the enclosing @BeforeEach").isEqualTo("opened");
        AFTER_EACH.incrementAndGet();
    }

    @AfterAll
    static void tornDownOnce() {
        assertThat(AFTER_EACH).hasValue(1);
    }

    @Nested
    class Inner {

        @Scenario("classpath:fixtures/three-steps.md")
        void scenarioInANestedClass() {}
    }
}
