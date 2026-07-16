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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import lombok.val;

/**
 * The human-readable rendering: a headline block (ended by {@value #HEADLINE_END},
 * which the Gradle plugin's {@code evaluateReport} task echoes to the console), then
 * one section per group (the suite class, or the source directory standalone) with a scenario table and, for every non-{@code
 * GROUNDED} step, both sides of the audit — the actor's claim, the judge's findings,
 * and the real tool trace.
 */
public class MarkdownReportRenderer implements EvaluationReportRenderer {

    /** Marks the end of the Markdown headline block. */
    public static final String HEADLINE_END = "<!-- headline-end -->";

    @Override
    public String name() {
        return "markdown";
    }

    @Override
    public List<Path> render(EvaluationReport report, Path dir) {
        val file = dir.resolve("evaluation.md");
        try {
            Files.writeString(file, renderMarkdown(report));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
        return List.of(file);
    }

    /** Renders the whole document. */
    public String renderMarkdown(EvaluationReport report) {
        val out = new StringBuilder("# Inquisitor evaluation report\n\n");
        appendHeadline(out, report);
        out.append('\n').append(HEADLINE_END).append('\n');
        for (val group : report.groups()) {
            appendGroup(out, group);
        }
        return out.toString();
    }

    private static void appendHeadline(StringBuilder out, EvaluationReport report) {
        val totals = report.totals();
        out.append("- Generated: ").append(report.generatedAt())
                .append(" (took ").append(ReportFormats.humanDuration(report.durationMillis())).append(")\n");
        if (report.header() != null) {
            out.append("- Header: ").append(report.header()).append('\n');
        }
        val runInfo = report.runInfo();
        if (runInfo != null) {
            out.append("- Actor: ").append(ReportFormats.endpoint(runInfo.actorModel(), runInfo.actorBaseUrl())).append('\n');
            out.append("- Judge: ").append(ReportFormats.endpoint(runInfo.judgeModel(), runInfo.judgeBaseUrl())).append('\n');
        }
        out.append("- Result: ").append(gate(totals)).append('\n');
        out.append("- Evaluation score: **").append(score(totals)).append("** (")
                .append(totals.stepsEvaluated()).append(" steps evaluated");
        if (totals.stepsNotEvaluated() > 0) {
            out.append(", ").append(totals.stepsNotEvaluated()).append(" not evaluated");
        }
        out.append(")\n");
        out.append("- Categories: ").append(totals.categories().entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "))).append('\n');
    }

    private static void appendGroup(StringBuilder out, EvaluationReport.Group group) {
        out.append("\n## ").append(group.name()).append('\n')
                .append("\n- Result: ").append(gate(group.totals()))
                .append(" — Score: **").append(score(group.totals())).append("**\n\n")
                .append("| Scenario | Expected | Steps | Result | Evaluation score |\n")
                .append("|---|---|---|---|---|\n");
        for (val scenario : group.scenarios()) {
            val evaluatedSteps = scenario.steps().stream().filter(StepEvaluationRecord::evaluated).toList();
            val mean = evaluatedSteps.isEmpty() ? Double.NaN : evaluatedSteps.stream()
                    .mapToDouble(StepEvaluationRecord::score).average().orElseThrow();
            out.append("| ").append(scenario.name())
                    .append(" | ").append(scenario.expectedOutcome())
                    .append(" | ").append(scenario.steps().size()).append('/')
                    .append(scenario.steps().getFirst().stepCount())
                    .append(" | ").append(resultLabel(scenario))
                    .append(" | ").append(Double.isNaN(mean) ? "—" : ReportFormats.percent(mean))
                    .append(" |\n");
        }
        appendFindings(out, group);
    }

    private static String resultLabel(EvaluationReport.ScenarioReport scenario) {
        if (scenario.passed()) {
            return "PASSED";
        }
        return scenario.missedDetection() ? "**FAILED (missed detection)**" : "**FAILED**";
    }

    private static void appendFindings(StringBuilder out, EvaluationReport.Group group) {
        val flagged = group.scenarios().stream()
                .flatMap(scenario -> scenario.steps().stream()
                        // Everything except a grounded, perfectly-scored step — mirrors the
                        // HTML renderer, so never-scored (NOT_EVALUATED) steps keep their
                        // diagnostic reason instead of vanishing from the Markdown findings.
                        .filter(step -> !(step.evaluated() && step.score() >= 1.0))
                        .map(step -> Map.entry(scenario, step)))
                .toList();
        if (flagged.isEmpty()) {
            return;
        }
        out.append("\n### Findings\n");
        for (val entry : flagged) {
            val scenario = entry.getKey();
            val step = entry.getValue();
            out.append("\n#### ").append(scenario.name())
                    .append(" — step ").append(step.stepIndex()).append(" \"").append(step.stepTitle())
                    .append("\" — ").append(step.category()).append('\n')
                    .append("- Actor verdict: ").append(step.outcome()).append(" — ")
                    .append(step.reasoning()).append('\n');
            if (!step.evidence().isEmpty()) {
                out.append("- Actor evidence: ").append(String.join("; ", step.evidence())).append('\n');
            }
            if (!step.feedback().isBlank()) {
                out.append("- Judge: ").append(step.feedback()).append('\n');
            }
            if (!step.toolCalls().isEmpty()) {
                out.append("- Trace:\n\n");
                step.toolCalls().forEach(call -> out.append("      ").append(call).append('\n'));
            }
        }
    }

    private static String gate(EvaluationReport.Totals totals) {
        return totals.scenariosPassed() + "/" + totals.scenarios()
                + " scenarios PASSED (expectation-aware: an expected failure that failed is a pass)";
    }

    private static String score(EvaluationReport.Totals totals) {
        return totals.meanScore() == null ? "n/a" : ReportFormats.percent(totals.meanScore());
    }

}
