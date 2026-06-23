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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.inquisitor.harness.HarnessDefaults;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.HttpTarget;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

/**
 * Drives the markdown scenarios against the running demo app via the standalone
 * harness ({@link ScenarioExecutor}), asserting on the verdicts ourselves — one
 * ordinary JUnit test per scenario.
 *
 * <p>Each test needs the local LLM (an OpenAI-compatible server at
 * {@code http://127.0.0.1:8000}, see {@code src/test/resources/application.yml}),
 * so the class is gated: run with {@code INQUISITOR_LLM_IT=true}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INQUISITOR_LLM_IT", matches = "true")
class ScenarioTests {

    @Autowired
    private ScenarioExecutor executor;

    @Autowired
    private ScenarioParser parser;

    @Autowired
    private HttpTargetRegistry httpTargetRegistry;

    @LocalServerPort
    private int port;

    @BeforeEach
    void registerApplicationTarget() {
        // The app's HTTP target depends on the random port, so it is registered here
        // rather than by the starter autoconfiguration. The datasource is auto-registered.
        httpTargetRegistry.register(HarnessDefaults.APPLICATION, HttpTarget.of("http://localhost:" + port));
    }

    @Test
    void fetchingANonExistentAccountReturnsProblemDetail() {
        runScenario("scenarios/account-not-found.md");
    }

    @Test
    void openingAnAccountAndDepositing() {
        runScenario("scenarios/open-account-and-deposit.md");
    }

    @Test
    void transferringBetweenAccounts() {
        runScenario("scenarios/transfer-between-accounts.md");
    }

    @Test
    void overdraftIsRejected() {
        runScenario("scenarios/overdraft-rejected.md");
    }

    @Test
    void transactionHistoryIsRecorded() {
        runScenario("scenarios/transaction-history.md");
    }

    @Test
    void databaseStateMatchesTheApi() {
        runScenario("scenarios/database-state.md");
    }

    @Test
    void importingAccountsFromCsvAndPlainText() {
        runScenario("scenarios/import-accounts.md");
    }

    private void runScenario(String classpathLocation) {
        val resource = new ClassPathResource(classpathLocation);
        val scenario = parser.parse(read(resource), resource.getFilename());
        val result = executor.evaluate(scenario);
        assertThat(result.passed()).withFailMessage(() -> describe(result)).isTrue();
    }

    private static String read(ClassPathResource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read scenario " + resource.getPath(), e);
        }
    }

    private static String describe(ScenarioResult result) {
        val message = new StringBuilder("Scenario '").append(result.scenario().name()).append("' failed:\n");
        for (val step : result.results()) {
            val verdict = step.verdict();
            message.append("  [").append(verdict.outcome()).append("] ")
                    .append(step.step().title()).append(" — ").append(verdict.reasoning()).append('\n');
            if (!step.passed() && !verdict.evidence().isEmpty()) {
                message.append("      evidence: ").append(verdict.evidence()).append('\n');
            }
        }
        return message.toString();
    }
}
