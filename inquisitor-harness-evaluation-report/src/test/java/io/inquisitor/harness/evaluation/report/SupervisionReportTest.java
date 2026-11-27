/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.inquisitor.harness.evaluation.report;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import io.inquisitor.harness.evaluation.*;
import io.inquisitor.harness.executor.*;
import io.inquisitor.harness.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;

class SupervisionReportTest {
    @TempDir Path directory;

    @Test
    void recoveredStepsKeepOriginalAnswerInEveryFormat() throws Exception {
        val report = report(false);
        assertThat(report.totals().stepsRecorded()).isEqualTo(1);
        assertThat(report.totals().acceptedAfterCorrection()).isEqualTo(1);
        assertThat(report.totals().acceptedInitially()).isZero();
        assertThat(report.totals().corrections()).isEqualTo(1);
        assertThat(report.groups().getFirst().scenarios()).hasSize(1);
        val markdown = new MarkdownReportRenderer().renderMarkdown(report);
        assertThat(markdown).contains("Attempt 1", "Attempt 2", "broken <script>", "ACCEPT");
        val htmlFiles = new HtmlReportRenderer().render(report, directory);
        val scenarioFile = htmlFiles.stream().filter(path -> path.getParent().endsWith("scenarios")).findFirst().orElseThrow();
        assertThat(Files.readString(scenarioFile)).contains("Attempt 1", "Attempt 2", "&lt;script&gt;")
                .doesNotContain("<script>");
        val json = new JsonReportRenderer().render(report, directory).getFirst();
        assertThat(Files.readString(json)).contains("rounds", "rawResponse", "broken <script>", "judgeElapsedMillis");
    }

    @Test
    void unresolvedExecutionNeverCountsAsFaultDetection() {
        val report = report(true);
        val scenario = report.groups().getFirst().scenarios().getFirst();
        assertThat(scenario.passed()).isFalse();
        assertThat(scenario.missedDetection()).isFalse();
        assertThat(scenario.unresolved()).isTrue();
        assertThat(report.totals().scenariosPassed()).isZero();
        assertThat(report.totals().unresolvedSteps()).isEqualTo(1);
        assertThat(report.totals().meanScore()).isNull();
        assertThat(new MarkdownReportRenderer().renderMarkdown(report)).contains("FAILED (unresolved execution)");
    }

    private static EvaluationReport report(boolean unresolved) {
        val step = new Step(1, "Verify", "Verify balance");
        val scenario = new Scenario("Bank", "", List.of(step), "scenarios/bank.md")
                .withExpectedOutcome(unresolved ? Outcome.FAIL : Outcome.PASS);
        val verdict = new StepVerdict(unresolved ? Outcome.FAIL : Outcome.PASS, "result", List.of());
        val first = new StepAttempt(1, "broken <script>", null, "Invalid JSON", List.of(), Duration.ofMillis(2));
        val second = new StepAttempt(2, "final response", verdict, null, List.of(), Duration.ofMillis(3));
        val review = new StepReview(unresolved ? StepReview.Decision.CORRECT : StepReview.Decision.ACCEPT,
                unresolved ? EvaluationCategory.UNSUPPORTED : EvaluationCategory.GROUNDED,
                List.of(), List.of(), "feedback");
        val rounds = List.of(new StepReviewRound(first, null, 0, "Fix JSON"),
                new StepReviewRound(second, review, 5, "feedback"));
        val recorder = new StepEvaluationRecorder();
        recorder.supervisionCompleted(StepRequest.of("c", scenario, step),
                new StepRun(verdict, List.of(), Duration.ofMillis(5), unresolved, List.of(first, second)),
                rounds, unresolved, unresolved ? "Budget exhausted" : "");
        return EvaluationReport.of(Instant.now(), Duration.ofMillis(10), null, null, recorder.records());
    }
}
