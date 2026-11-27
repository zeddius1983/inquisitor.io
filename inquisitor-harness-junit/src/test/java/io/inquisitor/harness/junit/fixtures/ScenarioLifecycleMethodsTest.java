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

package io.inquisitor.harness.junit.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.inquisitor.harness.junit.fixtures.FakeActor.FakeStep;
import io.inquisitor.harness.junit.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Pins that per-test lifecycle brackets a {@link Scenario @Scenario}, not each of its
 * steps: the class's {@link BeforeEach @BeforeEach} runs once before the first step and
 * its {@link AfterEach @AfterEach} once after the last — on the same test instance, so
 * tear-down sees what set-up initialized — and
 * {@link RecordApplicationEvents @RecordApplicationEvents} keeps the events of every step.
 */
@FakeActorTest
@RecordApplicationEvents
class ScenarioLifecycleMethodsTest {

    static final AtomicInteger BEFORE_EACH = new AtomicInteger();
    static final AtomicInteger AFTER_EACH = new AtomicInteger();
    /** (before-each, after-each) counts as each step saw them. */
    static final List<List<Integer>> SEEN_PER_STEP = new CopyOnWriteArrayList<>();

    /** Published by the fake actor at each step. */
    record StepEvent(int step) {}

    @Autowired
    ApplicationEvents events;

    @Scenario("classpath:fixtures/three-steps.md")
    void lifecycleBracketsTheScenario() {}

    /** Initialized by set-up; tear-down must see it although each step has its own instance. */
    private String resource;

    @BeforeEach
    void setUp() {
        BEFORE_EACH.incrementAndGet();
        resource = "opened";
    }

    @AfterEach
    void tearDown() {
        AFTER_EACH.incrementAndGet();
        assertThat(resource).as("field initialized by @BeforeEach").isEqualTo("opened");
        assertThat(events.stream(StepEvent.class).map(StepEvent::step))
                .as("events recorded across the scenario")
                .containsExactly(1, 2, 3);
    }

    @AfterAll
    static void ranOncePerScenario() {
        assertThat(SEEN_PER_STEP).as("(before-each, after-each) seen by each step")
                .containsExactly(List.of(1, 0), List.of(1, 0), List.of(1, 0));
        assertThat(BEFORE_EACH).hasValue(1);
        assertThat(AFTER_EACH).hasValue(1);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FakeActor.class)
    static class Config {

        @Bean
        FakeStep observeLifecycle(ApplicationEventPublisher publisher) {
            return request -> {
                SEEN_PER_STEP.add(List.of(BEFORE_EACH.get(), AFTER_EACH.get()));
                publisher.publishEvent(new StepEvent(request.step().index()));
            };
        }
    }
}
