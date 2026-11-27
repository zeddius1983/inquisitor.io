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
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.inquisitor.harness.HarnessDefaults;
import io.inquisitor.harness.junit.Scenario;
import io.inquisitor.harness.junit.fixtures.FakeActor.FakeStep;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.context.ContextConfiguration;

/**
 * Pins that {@link DirtiesContext @DirtiesContext} on a {@link Scenario @Scenario} dirties
 * the context around the scenario, not between its steps: every step runs in the same
 * application context — the live one, whose HTTP target points at its own port.
 *
 * <p>No web server is needed: each context gets a distinct {@code local.server.port}
 * (as a running server would) and a generation number. Each step records the generation
 * it ran in, the newest generation, and whether the HTTP target matches its port.
 */
@FakeActorTest
@ContextConfiguration(initializers = ScenarioDirtiesContextTest.PortPerContext.class)
class ScenarioDirtiesContextTest {

    static final AtomicInteger GENERATIONS = new AtomicInteger();
    static final List<Observation> OBSERVED = new CopyOnWriteArrayList<>();

    /** One step's view: the generation it ran in, the newest one, and the HTTP target check. */
    record Observation(int generation, int newest, boolean targetOnLivePort) {}

    @Scenario("classpath:fixtures/three-steps.md")
    @DirtiesContext(methodMode = MethodMode.BEFORE_METHOD)
    void dirtiedBefore() {}

    @Scenario("classpath:fixtures/three-steps.md")
    @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
    void dirtiedAfter() {}

    /** Runs once, after the scenario's last step. */
    @AfterEach
    void oneLiveContextPerScenario(TestInfo test) {
        var observed = List.copyOf(OBSERVED);
        OBSERVED.clear();

        assertThat(observed).as("steps of %s", test.getDisplayName()).hasSize(3);
        assertThat(observed)
                .as("every step of %s ran in the newest context", test.getDisplayName())
                .allSatisfy(step -> assertThat(step.generation()).isEqualTo(step.newest()));
        assertThat(observed).extracting(Observation::generation)
                .as("all steps of %s ran in one context", test.getDisplayName())
                .containsOnly(observed.getFirst().generation());
        assertThat(observed).extracting(Observation::targetOnLivePort)
                .as("the HTTP target pointed at the live port in %s", test.getDisplayName())
                .containsOnly(true);
    }

    /** Gives every context its own port, as a context with a running server would have. */
    static class PortPerContext implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final AtomicInteger NEXT_PORT = new AtomicInteger(20_000);

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "port", Map.of("local.server.port", String.valueOf(NEXT_PORT.incrementAndGet()))));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FakeActor.class)
    static class Config {

        @Bean
        FakeStep observeContext(Environment environment, HttpTargetRegistry targets) {
            var generation = GENERATIONS.incrementAndGet();
            return request -> {
                var port = environment.getProperty("local.server.port");
                var target = targets.resolve(HarnessDefaults.APPLICATION).baseUrl();
                OBSERVED.add(new Observation(generation, GENERATIONS.get(), target.endsWith(":" + port)));
            };
        }
    }
}
