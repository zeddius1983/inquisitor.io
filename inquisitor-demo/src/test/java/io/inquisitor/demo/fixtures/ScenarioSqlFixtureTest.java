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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import io.inquisitor.demo.fixtures.FakeActor.FakeStep;
import io.inquisitor.harness.junit.Scenario;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;

/**
 * Pins that an {@link Sql @Sql} fixture on a {@link Scenario @Scenario} applies to the
 * scenario as a whole — once, before the first step — not before every step, while a
 * {@link ParameterizedTest @ParameterizedTest} in the same class keeps Spring's usual
 * once-per-invocation behaviour.
 *
 * <p>The fake actor plays the scenario's transfer directly in the database and records
 * Dave's balance at each step. Seeded once, it reads 500 → 300 → 300; re-seeded before
 * each step, step 3 would read 500 again.
 */
@FakeActorTest
class ScenarioSqlFixtureTest {

    private static final long DAVE = 900001L;
    private static final int TRANSFER_STEP = 2;

    /** Dave's balance as observed by each step, in order. */
    static final List<BigDecimal> DAVE_BALANCE_PER_STEP = new CopyOnWriteArrayList<>();

    @Autowired
    DataSource dataSource;

    @Scenario
    @Sql("/fixtures/seeded-accounts.sql")
    void transferBetweenSeededAccounts() {}

    /** Each invocation is its own test, so each starts from a freshly seeded 500. */
    @ParameterizedTest
    @ValueSource(ints = {100, 200})
    @Sql("/fixtures/seeded-accounts.sql")
    void parameterizedTestIsSeededPerInvocation(int amount) {
        val jdbc = JdbcClient.create(dataSource);
        jdbc.sql("UPDATE account SET balance = balance - ? WHERE id = ?").param(amount).param(DAVE).update();

        assertThat(balance(jdbc)).isEqualByComparingTo(BigDecimal.valueOf(500L - amount));
    }

    @AfterAll
    static void fixtureAppliedOncePerScenario() {
        assertThat(DAVE_BALANCE_PER_STEP)
                .as("Dave's balance per step (a re-seed between steps would reset it to 500)")
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("300"));
    }

    private static BigDecimal balance(JdbcClient jdbc) {
        return jdbc.sql("SELECT balance FROM account WHERE id = ?").param(DAVE).query(BigDecimal.class).single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Steps {

        @Bean
        FakeStep transferAndObserve(DataSource dataSource) {
            val jdbc = JdbcClient.create(dataSource);
            return request -> {
                if (request.step().index() == TRANSFER_STEP) {
                    jdbc.sql("UPDATE account SET balance = balance - 200 WHERE id = ?").param(DAVE).update();
                }
                DAVE_BALANCE_PER_STEP.add(balance(jdbc));
            };
        }
    }
}
