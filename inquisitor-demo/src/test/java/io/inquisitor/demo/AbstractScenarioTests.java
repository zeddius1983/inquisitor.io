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
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.HttpTarget;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;

/**
 * Shared plumbing for the standalone (executor-driven) scenario suites: parses the
 * markdown scenarios, registers the running app as the HTTP target, and — when step
 * evaluation is enabled — reports the judge's scores.
 *
 * <p>Concrete subclasses differ only in what they assert: {@link ScenarioTests} runs the
 * correct scenarios and expects them to pass, while {@link FaultDetectionTests} runs them
 * against a buggy build and expects a failure at the seeded step. Both drive the harness
 * the same way, so that lives here.
 *
 * <p>Gated on the LLM; run with {@code INQUISITOR_LLM_IT=true}. The gate lives here via
 * {@link RequiresLlm @RequiresLlm} ({@code @Inherited}), so the concrete subclasses inherit
 * it — as does {@code @SpringBootTest} (which Spring walks up the hierarchy for). The
 * {@code PER_CLASS} lifecycle lets the non-static {@link #reportSuiteEvaluation()} hook read
 * the injected recorder.
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiresLlm
abstract class AbstractScenarioTests {

    @Autowired
    protected ScenarioExecutor executor;

    @Autowired
    protected ScenarioParser parser;

    @Autowired
    private HttpTargetRegistry httpTargetRegistry;

    // Present only when step evaluation is enabled (INQUISITOR_EVAL=true); the run is
    // scored but not gated on the score — the subclass assertions are unchanged.
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

    /** Parses a scenario markdown resource from the test classpath. */
    protected Scenario parse(String classpathLocation) {
        val resource = new ClassPathResource(classpathLocation);
        return parser.parse(read(resource), resource.getFilename());
    }

    /** The number of steps recorded so far, or 0 when evaluation is disabled. */
    protected int recordedCount() {
        val recorder = stepEvaluationRecorder.getIfAvailable();
        return recorder == null ? 0 : recorder.records().size();
    }

    /**
     * When step evaluation is enabled, log the judge's per-step scores for one run. Reports
     * only the records appended since {@code recordedBefore} (from {@link #recordedCount()}),
     * so a suite that drives the same scenario more than once keeps the read-outs separate.
     * Purely a calibration read-out — it never affects the subclass assertions.
     *
     * @param label          how to name this run in the header (e.g. {@code scenario 'X'})
     * @param recordedBefore the recorder size captured before the run
     */
    protected void reportEvaluation(String label, int recordedBefore) {
        val recorder = stepEvaluationRecorder.getIfAvailable();
        if (recorder == null) {
            return;
        }
        val steps = recorder.records().stream().skip(recordedBefore).toList();
        if (steps.isEmpty()) {
            return;
        }
        val score = steps.stream().mapToDouble(StepEvaluationRecord::score).average().orElse(0.0);
        val message = new StringBuilder(String.format(
                "%n[evaluation] %s — score %.0f%%%n", label, score * 100));
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
     * After all runs, log the suite-level overall score and a one-line-per-scenario roll-up.
     * Requires the PER_CLASS lifecycle so this non-static hook can read the injected recorder.
     * No-op when evaluation is disabled.
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

    /** A human-readable transcript of a scenario's per-step verdicts, for assertion messages. */
    protected static String describe(ScenarioResult result) {
        val message = new StringBuilder("Scenario '").append(result.scenario().name()).append("' result:\n");
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

    private static String read(ClassPathResource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read scenario " + resource.getPath(), e);
        }
    }
}
