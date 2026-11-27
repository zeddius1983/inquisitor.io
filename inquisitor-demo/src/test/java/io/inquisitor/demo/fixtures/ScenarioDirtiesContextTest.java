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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.inquisitor.demo.fixtures.FakeActor.FakeStep;
import io.inquisitor.harness.HarnessDefaults;
import io.inquisitor.harness.junit.Scenario;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;

/**
 * Pins that {@link DirtiesContext @DirtiesContext} on a {@link Scenario @Scenario} dirties
 * the context around the scenario, not between its steps: every step runs in the same
 * application context — the live one, reachable on its own port — so the conversation and
 * the app under test survive from the first step to the last.
 *
 * <p>Each context the suite starts gets a generation number; each step records the
 * generation it ran in, the newest generation, and whether the harness's HTTP target
 * points at the port of the context it ran in.
 */
@FakeActorTest
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
        val observed = List.copyOf(OBSERVED);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class Steps {

        @Bean
        FakeStep observeContext(Environment environment, HttpTargetRegistry targets) {
            val generation = GENERATIONS.incrementAndGet();
            return _ -> {
                val port = environment.getProperty("local.server.port");
                val target = targets.resolve(HarnessDefaults.APPLICATION).baseUrl();
                OBSERVED.add(new Observation(generation, GENERATIONS.get(), target.endsWith(":" + port)));
            };
        }
    }
}
