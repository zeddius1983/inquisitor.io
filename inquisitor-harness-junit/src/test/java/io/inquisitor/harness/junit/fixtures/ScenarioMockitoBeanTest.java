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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.inquisitor.harness.junit.fixtures.FakeActor.FakeStep;
import io.inquisitor.harness.junit.Scenario;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Pins that {@link MockitoBean @MockitoBean} mocks are reset around a
 * {@link Scenario @Scenario}, not between its steps: a stub made at step 1 still answers
 * at step 3, and the calls of every step are there to verify at the end.
 */
@FakeActorTest
class ScenarioMockitoBeanTest {

    /** A collaborator to mock; the context has no bean of this type, so the mock is added. */
    interface Greeter {
        @Nullable String greet();
    }

    static final List<@Nullable String> GREETINGS = new CopyOnWriteArrayList<>();

    @MockitoBean
    Greeter greeter;

    @Scenario("classpath:fixtures/three-steps.md")
    void stubSurvivesTheScenario() {}

    /** Runs once, after the scenario's last step (before the mock is reset). */
    @AfterEach
    void stubAndCallsSpanEveryStep() {
        assertThat(GREETINGS).as("greeting per step").containsExactly("hello", "hello", "hello");
        verify(greeter, times(3)).greet();
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FakeActor.class)
    static class Config {

        @Bean
        FakeStep greet(Greeter greeter) {
            return request -> {
                if (request.step().index() == 1) {
                    given(greeter.greet()).willReturn("hello");
                }
                GREETINGS.add(greeter.greet());
            };
        }
    }
}
