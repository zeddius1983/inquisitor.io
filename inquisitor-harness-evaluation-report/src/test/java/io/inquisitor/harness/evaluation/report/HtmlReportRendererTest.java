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

package io.inquisitor.harness.evaluation.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.model.Outcome;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

class HtmlReportRendererTest {

    @TempDir
    private Path dir;

    private String index;
    private String cucumberPage;
    private String transferPage;
    private String faultPage;

    private static StepEvaluationRecord.StepEvaluationRecordBuilder step(String scenario,
            @Nullable String source, Outcome expected, int stepIndex, int stepCount) {
        return StepEvaluationRecord.builder()
                .scenario(scenario).scenarioSource(source).expectedOutcome(expected)
                .stepIndex(stepIndex).stepCount(stepCount).stepTitle("step " + stepIndex)
                .outcome(Outcome.PASS).reasoning("fine").evidence(List.of())
                .toolCalls(List.of()).elapsedMillis(10)
                .score(1.0).category("GROUNDED").feedback("");
    }

    private static EvaluationReport sampleReport() {
        val cucumber = "classpath:scenarios/cucumber/transfer.md";
        val fault = "classpath:scenarios/explicit/deposit.md";
        return EvaluationReport.of(Instant.parse("2026-07-15T12:00:00Z"), Duration.ofMinutes(65),
                "run label", new EvaluationRunInfo("actor-model", "http://a", "judge-model", "http://j"),
                List.of(
                        // contradicted-PASS: success 100%, evaluation score 50% — the headline gap
                        step("Transfer funds", cucumber, Outcome.PASS, 1, 2).build(),
                        step("Transfer funds", cucumber, Outcome.PASS, 2, 2)
                                .score(0.0).category("CONTRADICTED")
                                .reasoning("cites <script>alert(\"x\")</script> & \"balances\"")
                                .evidence(List.of("HTTP 200 <body>"))
                                .toolCalls(List.of("#0 sqlQuery(SELECT a & b) -> [{balance=50}]"))
                                .feedback("claimed reload; the trace shows none")
                                .build(),
                        // expected FAIL but fully green: a missed detection
                        step("Deposit fault", fault, Outcome.FAIL, 1, 2).build(),
                        // a never-scored step: the reason must surface as a finding
                        step("Deposit fault", fault, Outcome.FAIL, 2, 2)
                                .category(StepEvaluationRecord.NOT_EVALUATED)
                                .feedback("The judge call failed (timeout); not evaluated.")
                                .build()));
    }

    @BeforeEach
    void render() throws IOException {
        val files = new HtmlReportRenderer().render(sampleReport(), dir);
        assertThat(files).allSatisfy(file -> assertThat(file).exists());
        index = Files.readString(dir.resolve("evaluation.html"));
        cucumberPage = Files.readString(dir.resolve("buckets/cucumber.html"));
        transferPage = Files.readString(dir.resolve("scenarios/cucumber-0.html"));
        faultPage = Files.readString(dir.resolve("scenarios/explicit-0.html"));
    }

    @Test
    void indexPageCarriesTilesAndLinksToBucketPages() {
        assertThat(index)
                // tiles: scenarios passed, evaluation score (mean of 3 evaluated steps), duration
                .contains("<div class=\"value\">1/2</div>")
                .contains("<div class=\"value\">66.7%</div>")
                .contains("<div class=\"value\">1h 5m 0s</div>")
                .contains("Header: run label")
                .contains("Actor: actor-model @ http://a")
                .contains("<th>Evaluation score</th>")
                .contains("<a href=\"buckets/cucumber.html\">cucumber</a>")
                .contains("<a href=\"buckets/explicit.html\">explicit</a>")
                // success rate is expectation-aware: cucumber 1/1 passed, explicit 0/1
                .contains("<td>1/1</td><td>100.0%</td>")
                .contains("<td>0/1</td><td>0.0%</td>");
        assertThat(index).doesNotContain("Evaluation rate");
    }

    @Test
    void bucketPageShowsResultAndLinksToScenarioPages() {
        assertThat(cucumberPage)
                .contains("<a href=\"../evaluation.html\">report</a> &rsaquo; cucumber")
                // the contradicted-PASS row: PASSED result, evaluation score 50% — the gap
                .contains("<td><a href=\"../scenarios/cucumber-0.html\">Transfer funds</a></td>"
                        + "<td>PASS</td><td>2/2</td>"
                        + "<td><span class=\"ok\">PASSED</span></td><td>50.0%</td>");
        assertThat(Files.exists(dir.resolve("buckets/explicit.html"))).isTrue();
    }

    @Test
    void scenarioPageCarriesStepTableFindingsAndBreadcrumb() {
        assertThat(transferPage)
                .contains("<a href=\"../buckets/cucumber.html\">cucumber</a> &rsaquo; Transfer funds")
                .contains("<td>2/2</td><td>step 2</td><td><span class=\"ok\">PASS</span></td>"
                        + "<td>CONTRADICTED</td><td>0.0%</td><td>10 ms</td>")
                .contains("<details><summary>Step 2 “step 2” &mdash; CONTRADICTED</summary>")
                .contains("<p><b>Judge:</b> claimed reload; the trace shows none</p>")
                .contains("<pre>#0 sqlQuery(SELECT a &amp; b) -&gt; [{balance=50}]</pre>");
        // the grounded step produces no finding
        assertThat(transferPage).doesNotContain("<summary>Step 1");
    }

    @Test
    void missedDetectionAndJudgeFailureSurfaceOnTheFaultScenarioPage() {
        assertThat(faultPage)
                .contains("<span class=\"bad\">FAILED (missed detection)</span>")
                .contains("<details><summary>Step 2 “step 2” &mdash; NOT_EVALUATED</summary>")
                .contains("The judge call failed (timeout); not evaluated.");
    }

    @Test
    void everyInterpolatedValueIsHtmlEscaped() {
        assertThat(transferPage)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &quot;balances&quot;")
                .contains("HTTP 200 &lt;body&gt;");
    }
}
