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

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.junit.Harness;
import io.inquisitor.harness.junit.Scenario;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.StepVerdict;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;

/**
 * Pins that an {@link Sql @Sql} fixture on a {@link Scenario @Scenario} applies to the
 * scenario as a whole — once, before the first step — not before every step.
 *
 * <p>Deterministic and LLM-free (so it runs in every build, unlike the gated suites): the
 * actor is replaced by a fake that plays the scenario's transfer directly in the database
 * and records Dave's balance at each step. Seeded once, the balance reads 500 → 300 → 300;
 * re-seeded before each step, step 3 would read 500 again. Evaluation is switched off so
 * no judge is consulted on the fake verdicts.
 */
@Harness(scenarioDir = "classpath:scenarios/explicit/")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "inquisitor.harness.evaluation.enabled=false")
class ScenarioSqlFixtureTest {

    private static final long DAVE = 900001L;
    private static final int TRANSFER_STEP = 2;

    /** Dave's balance as observed by each step, in order. */
    static final List<BigDecimal> DAVE_BALANCE_PER_STEP = new CopyOnWriteArrayList<>();

    @Scenario
    @Sql("/fixtures/seeded-accounts.sql")
    void transferBetweenSeededAccounts() {}

    @AfterAll
    static void fixtureAppliedOncePerScenario() {
        assertThat(DAVE_BALANCE_PER_STEP)
                .as("Dave's balance per step (a re-seed between steps would reset it to 500)")
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("300"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeActor {

        /** Replaces the LLM-backed executor: each step acts on and reads the database directly. */
        @Bean
        ScenarioExecutor scenarioExecutor(DataSource dataSource) {
            val jdbc = JdbcClient.create(dataSource);
            return new ScenarioExecutor(request -> {
                if (request.step().index() == TRANSFER_STEP) {
                    jdbc.sql("UPDATE account SET balance = balance - 200 WHERE id = ?").param(DAVE).update();
                }
                DAVE_BALANCE_PER_STEP.add(jdbc.sql("SELECT balance FROM account WHERE id = ?")
                        .param(DAVE)
                        .query(BigDecimal.class)
                        .single());
                return new StepRun(new StepVerdict(Outcome.PASS, "fake actor", List.of()), List.of(), Duration.ZERO);
            });
        }
    }
}
