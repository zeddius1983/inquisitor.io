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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.inquisitor.harness.junit.Scenario;
import io.inquisitor.harness.junit.fixtures.FakeActor.FakeStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Pins that a declared argument source yielding no arguments fails the scenario before
 * any step runs — as for {@code @ParameterizedTest} — instead of being taken for an
 * unparameterized scenario whose steps would hit the application without their values.
 */
class ScenarioEmptyArgumentsTest {

    static volatile boolean launched;
    static final AtomicInteger STEPS_RUN = new AtomicInteger();

    @Test
    void emptyArgumentSourceFailsBeforeAnyStep() {
        var result = new AtomicReference<TestExecutionResult>();
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(EmptySourceSuite.class))
                .build();
        launched = true;
        try {
            LauncherFactory.create().execute(request, new TestExecutionListener() {
                @Override
                public void executionFinished(TestIdentifier identifier, TestExecutionResult finished) {
                    if (identifier.getDisplayName().equals("noCases(String)")) {
                        result.set(finished);
                    }
                }
            });
        } finally {
            launched = false;
        }

        assertThat(result.get()).isNotNull();
        assertThat(result.get().getThrowable())
                .hasValueSatisfying(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Scenario noCases declares an argument source, but it provided no arguments"));
        assertThat(STEPS_RUN).as("steps run").hasValue(0);
    }

    static boolean launched() {
        return launched;
    }

    /** Runs only when launched by the test above, not by the build's own test scan. */
    @FakeActorTest
    @EnabledIf("io.inquisitor.harness.junit.fixtures.ScenarioEmptyArgumentsTest#launched")
    static class EmptySourceSuite {

        @Scenario("classpath:fixtures/three-steps.md")
        @MethodSource("cases")
        void noCases(String currency) {}

        static Stream<Arguments> cases() {
            return Stream.empty();
        }

        @Configuration(proxyBeanMethods = false)
        @Import(FakeActor.class)
        static class Config {

            @Bean
            FakeStep countSteps() {
                return request -> STEPS_RUN.incrementAndGet();
            }
        }
    }
}
