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

import io.inquisitor.harness.junit.Scenario;
import io.inquisitor.harness.junit.fixtures.FakeActor.FakeStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * Pins that an {@link Sql @Sql} fixture on a {@link Scenario @Scenario} applies to the
 * scenario as a whole — the before-script once ahead of the first step, the after-script
 * once after the last — while a {@link ParameterizedTest @ParameterizedTest} in the same
 * class keeps Spring's usual once-per-invocation behaviour. The {@link RecordingDataSource}
 * records the statements the fixtures ran, so no database is involved.
 */
@FakeActorTest
class ScenarioSqlFixtureTest {

    private static final String SEED = "'scenario-seed'";
    private static final String CLEANUP = "LIKE 'scenario-%'";

    static final List<Long> SEEDS_SEEN_PER_STEP = new CopyOnWriteArrayList<>();
    static final List<Long> CLEANUPS_SEEN_PER_STEP = new CopyOnWriteArrayList<>();
    static final List<Long> PARAM_SEEDS_SEEN = new CopyOnWriteArrayList<>();
    static RecordingDataSource recorded;

    @Autowired
    RecordingDataSource dataSource;

    @Scenario("classpath:fixtures/three-steps.md")
    @Sql("/fixtures/scenario-seed.sql")
    @Sql(scripts = "/fixtures/scenario-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void seededOncePerScenario() {}

    /** Each invocation is its own test, so each is seeded afresh. */
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    @Sql("/fixtures/param-seed.sql")
    void parameterizedTestIsSeededPerInvocation(int invocation) {
        PARAM_SEEDS_SEEN.add(dataSource.count("'param-seed'"));
    }

    @AfterAll
    static void fixturesBracketTheScenario() {
        assertThat(SEEDS_SEEN_PER_STEP).as("seed statements run by each step (re-seeded per step: 1, 2, 3)")
                .containsExactly(1L, 1L, 1L);
        assertThat(CLEANUPS_SEEN_PER_STEP).as("clean-up statements run by each step (after every step: 0, 1, 2)")
                .containsExactly(0L, 0L, 0L);
        assertThat(recorded.count(CLEANUP)).as("clean-up after the last step").isEqualTo(1);
        assertThat(PARAM_SEEDS_SEEN).as("param seeds seen per invocation").containsExactly(1L, 2L);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FakeActor.class)
    static class Config {

        @Bean
        RecordingDataSource dataSource() {
            recorded = new RecordingDataSource();
            return recorded;
        }

        @Bean
        FakeStep observeFixtures(RecordingDataSource dataSource) {
            return request -> {
                SEEDS_SEEN_PER_STEP.add(dataSource.count(SEED));
                CLEANUPS_SEEN_PER_STEP.add(dataSource.count(CLEANUP));
            };
        }
    }
}
