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

import static org.assertj.core.api.Assertions.assertThat;

import io.inquisitor.harness.executor.ScenarioExecutor;
import lombok.val;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Drives the markdown scenarios against the running demo app via the standalone
 * harness ({@link ScenarioExecutor}), asserting on the verdicts ourselves — one
 * ordinary JUnit test per scenario.
 *
 * <p>The shared harness plumbing (target registration, parsing, and the optional
 * evaluation read-out) lives in {@link AbstractScenarioTests}; this class only adds the
 * positive assertion that each scenario passes. Each test needs the local LLM, so it is
 * gated: run with {@code INQUISITOR_LLM_IT=true}.
 *
 * <p>Tagged {@code inquisitor} so the Gradle plugin's {@code evaluate} task selects it
 * alongside the {@code @Harness} suites (the fault-detection suites stay untagged).
 */
@Tag("inquisitor")
class ScenarioTests extends AbstractScenarioTests {

    @Test
    void fetchingANonExistentAccountReturnsProblemDetail() {
        runScenario("scenarios/explicit/account-not-found.md");
    }

    @Test
    void openingAnAccountAndDepositing() {
        runScenario("scenarios/explicit/open-account-and-deposit.md");
    }

    @Test
    void transferringBetweenAccounts() {
        runScenario("scenarios/explicit/transfer-between-accounts.md");
    }

    @Test
    void overdraftIsRejected() {
        runScenario("scenarios/explicit/overdraft-rejected.md");
    }

    @Test
    void transactionHistoryIsRecorded() {
        runScenario("scenarios/explicit/transaction-history.md");
    }

    @Test
    void databaseStateMatchesTheApi() {
        runScenario("scenarios/explicit/database-state.md");
    }

    @Test
    void importingAccountsFromCsvAndPlainText() {
        runScenario("scenarios/explicit/import-accounts.md");
    }

    private void runScenario(String classpathLocation) {
        val scenario = parse(classpathLocation);
        val recordedBefore = recordedCount();
        val result = executor.execute(scenario);
        reportEvaluation("scenario '" + scenario.name() + "'", recordedBefore);
        assertThat(result.passed()).withFailMessage(() -> describe(result)).isTrue();
    }
}
