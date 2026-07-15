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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.model.Outcome;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

class HtmlReportRendererTest {

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
                        // contradicted-PASS: success 100%, evaluation 50% — the headline gap
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

    @Test
    void rendersTilesAndTheBucketsOverview() {
        val html = new HtmlReportRenderer().render(sampleReport());

        assertThat(html)
                // tiles: gate, evaluation score (mean of the 3 evaluated steps), duration
                .contains("<div class=\"value\">1/2</div>")
                .contains("<div class=\"value\">66.7%</div>")
                .contains("<div class=\"value\">1h 5m 0s</div>")
                .contains("Header: run label")
                .contains("Actor: actor-model @ http://a")
                // the overview links into the bucket sections
                .contains("<a href=\"#bucket-cucumber\">cucumber</a>")
                .contains("<a href=\"#bucket-explicit\">explicit</a>")
                .contains("<h2 id=\"bucket-cucumber\">cucumber</h2>");
    }

    @Test
    void bucketTablesShowBothRatesAndLinkIntoScenarioSections() {
        val html = new HtmlReportRenderer().render(sampleReport());

        assertThat(html)
                // the contradicted-PASS row: success rate 100%, evaluation rate 50%
                .contains("<td><a href=\"#scenario-cucumber-0\">Transfer funds</a></td>"
                        + "<td>PASS</td><td>2/2</td><td>100.0%</td><td>50.0%</td>"
                        + "<td><span class=\"ok\">yes</span></td>")
                // the fully-green fault run reads as a missed detection
                .contains("<td><a href=\"#scenario-explicit-0\">Deposit fault</a></td>"
                        + "<td>FAIL</td><td>2/2</td><td>100.0%</td><td>100.0%</td>"
                        + "<td><span class=\"bad\">MISSED DETECTION</span></td>")
                // the linked scenario section exists, with its per-step table
                .contains("<h3 id=\"scenario-cucumber-0\">Transfer funds")
                .contains("<td>2/2</td><td>step 2</td><td><span class=\"ok\">PASS</span></td>"
                        + "<td>CONTRADICTED</td><td>0.0%</td><td>10 ms</td>")
                .contains("back to top");
    }

    @Test
    void findingsAreExpandableAndCarryBothSides() {
        val html = new HtmlReportRenderer().render(sampleReport());

        assertThat(html)
                .contains("<details><summary>Step 2 “step 2” &mdash; CONTRADICTED</summary>")
                .contains("<p><b>Judge:</b> claimed reload; the trace shows none</p>")
                .contains("<pre>#0 sqlQuery(SELECT a &amp; b) -&gt; [{balance=50}]</pre>")
                // never-scored steps surface their reason as a finding too
                .contains("<details><summary>Step 2 “step 2” &mdash; NOT_EVALUATED</summary>")
                .contains("The judge call failed (timeout); not evaluated.");
        // the grounded steps produce no findings
        assertThat(html).doesNotContain("<summary>Step 1");
    }

    @Test
    void everyInterpolatedValueIsHtmlEscaped() {
        val html = new HtmlReportRenderer().render(sampleReport());

        assertThat(html)
                .doesNotContain("<script>")
                .contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &quot;balances&quot;")
                .contains("HTTP 200 &lt;body&gt;");
    }
}
