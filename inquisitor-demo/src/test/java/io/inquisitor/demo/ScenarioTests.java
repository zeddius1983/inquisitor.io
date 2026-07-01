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
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import io.inquisitor.harness.HarnessDefaults;
import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.evaluation.StepEvaluationRecorder;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.junit.RequiresLlm;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.HttpTarget;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.ObjectProvider;
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
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiresLlm
class ScenarioTests {

    @Autowired
    private ScenarioExecutor executor;

    @Autowired
    private ScenarioParser parser;

    @Autowired
    private HttpTargetRegistry httpTargetRegistry;

    // Present only when step evaluation is enabled (INQUISITOR_EVAL=true); the run is
    // scored but not gated on the score — the pass/fail assertion is unchanged.
    @Autowired
    private ObjectProvider<StepEvaluationRecorder> stepEvaluationRecorder;

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
        val resource = new ClassPathResource(classpathLocation);
        val scenario = parser.parse(read(resource), resource.getFilename());
        val result = executor.execute(scenario);
        reportEvaluation(scenario.name());
        assertThat(result.passed()).withFailMessage(() -> describe(result)).isTrue();
    }

    /**
     * When step evaluation is enabled, log the judge's per-step scores for this scenario.
     * Purely a calibration read-out — it never affects the assertion above.
     */
    private void reportEvaluation(String scenarioName) {
        val recorder = stepEvaluationRecorder.getIfAvailable();
        if (recorder == null) {
            return;
        }
        val steps = recorder.records().stream()
                .filter(record -> record.scenario().equals(scenarioName))
                .toList();
        if (steps.isEmpty()) {
            return;
        }
        val score = steps.stream().mapToDouble(StepEvaluationRecord::score).average().orElse(0.0);
        val message = new StringBuilder(String.format(
                "%n[evaluation] scenario '%s' — score %.0f%%%n", scenarioName, score * 100));
        for (val step : steps) {
            message.append(String.format("  #%d %-18s (%.2f) %s%n",
                    step.stepIndex(),
                    step.category() != null ? step.category() : "UNSCORED",
                    step.score(),
                    step.stepTitle()));
            if (!step.feedback().isBlank()) {
                message.append("       ").append(step.feedback().replace("\n", "\n       ")).append('\n');
            }
        }
        log.info(message.toString());
    }

    /**
     * After all scenarios, log the suite-level overall score and a one-line-per-scenario
     * roll-up. Requires the PER_CLASS lifecycle so this non-static hook can read the
     * injected recorder. No-op when evaluation is disabled.
     */
    @AfterAll
    void reportSuiteEvaluation() {
        val recorder = stepEvaluationRecorder.getIfAvailable();
        if (recorder == null || recorder.records().isEmpty()) {
            return;
        }
        val perScenario = recorder.records().stream().collect(Collectors.groupingBy(
                StepEvaluationRecord::scenario, LinkedHashMap::new,
                Collectors.averagingDouble(StepEvaluationRecord::score)));
        val overall = recorder.overallScore().orElse(0.0);
        val message = new StringBuilder(String.format(
                "%n[evaluation] SUITE — overall score %.0f%% (%d steps across %d scenarios)%n",
                overall * 100, recorder.records().size(), perScenario.size()));
        perScenario.forEach((scenario, mean) ->
                message.append(String.format("  %4.0f%%  %s%n", mean * 100, scenario)));
        log.info(message.toString());
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
