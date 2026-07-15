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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class EvaluationReportWriterTest {

    @TempDir
    private Path dir;

    private static EvaluationReport sampleReport() {
        val grounded = StepEvaluationRecord.builder()
                .scenario("Transfer funds")
                .scenarioSource("classpath:scenarios/cucumber/transfer.md")
                .expectedOutcome(Outcome.PASS)
                .stepIndex(1).stepCount(2).stepTitle("Make the transfer")
                .outcome(Outcome.PASS)
                .reasoning("transferred")
                .evidence(List.of("HTTP 200"))
                .toolCalls(List.of("#0 httpRequest({}) -> HTTP 200"))
                .elapsedMillis(1200)
                .score(1.0).category("GROUNDED").feedback("")
                .build();
        val contradicted = StepEvaluationRecord.builder()
                .scenario("Transfer funds")
                .scenarioSource("classpath:scenarios/cucumber/transfer.md")
                .expectedOutcome(Outcome.PASS)
                .stepIndex(2).stepCount(2).stepTitle("Check the balance")
                .outcome(Outcome.PASS)
                .reasoning("balance matches")
                .evidence(List.of("balance 100"))
                .toolCalls(List.of("#0 sqlQuery(SELECT) -> [{balance=50}]"))
                .elapsedMillis(800)
                .score(0.0).category("CONTRADICTED")
                .feedback("claims balance 100; the trace shows 50")
                .build();
        return EvaluationReport.of(Instant.parse("2026-07-14T18:00:00Z"), Duration.ofMinutes(65),
                "gpt-oss-20b / reasoning off",
                new EvaluationRunInfo("gpt-oss-20b", "http://127.0.0.1:8000",
                        "Qwen3.6-35B", "http://127.0.0.1:8001"),
                List.of(grounded, contradicted));
    }

    @Test
    void discoversAllBuiltInRenderersAndWritesOneFileEach() throws IOException {
        val files = EvaluationReportWriter.discover().write(sampleReport(), dir.resolve("reports"));

        assertThat(files).extracting(file -> file.getFileName().toString())
                .containsExactlyInAnyOrder("evaluation.json", "evaluation.md", "evaluation.html");
        for (val file : files) {
            assertThat(file).exists();
        }

        // the JSON round-trips and holds the full detail
        val json = JsonMapper.builder().build()
                .readTree(Files.readString(dir.resolve("reports/evaluation.json")));
        assertThat(json.get("header").asString()).isEqualTo("gpt-oss-20b / reasoning off");
        assertThat(json.get("totals").get("stepsEvaluated").asInt()).isEqualTo(2);
        assertThat(json.get("buckets").get(0).get("name").asString()).isEqualTo("cucumber");
        assertThat(json.get("buckets").get(0).get("scenarios").get(0)
                .get("steps").get(1).get("feedback").asString())
                .isEqualTo("claims balance 100; the trace shows 50");
    }

    @Test
    void markdownHeadlineEndsAtTheMarkerAndCarriesTheAggregates() {
        val markdown = new MarkdownReportRenderer().render(sampleReport());

        val headline = markdown.substring(0, markdown.indexOf(MarkdownReportRenderer.HEADLINE_END));
        assertThat(headline)
                .contains("Header: gpt-oss-20b / reasoning off")
                .contains("Actor: gpt-oss-20b @ http://127.0.0.1:8000")
                .contains("Judge: Qwen3.6-35B @ http://127.0.0.1:8001")
                // the contradicted step still has a PASS outcome: the gate holds, the score drops —
                // exactly the "100% passed / 50% score" gap the report exists to expose
                .contains("1/1 scenarios matched")
                .contains("50.0%")
                .contains("took 1h 5m 0s");
    }

    @Test
    void findingsShowBothSidesOfANonGroundedStep() {
        val markdown = new MarkdownReportRenderer().render(sampleReport());

        val findings = markdown.substring(markdown.indexOf("### Findings"));
        assertThat(findings)
                .contains("Transfer funds — step 2 \"Check the balance\" — CONTRADICTED")
                .contains("Actor verdict: PASS — balance matches")
                .contains("Actor evidence: balance 100")
                .contains("Judge: claims balance 100; the trace shows 50")
                .contains("#0 sqlQuery(SELECT) -> [{balance=50}]");
        // the grounded step produces no finding
        assertThat(findings).doesNotContain("Make the transfer");
    }
}
