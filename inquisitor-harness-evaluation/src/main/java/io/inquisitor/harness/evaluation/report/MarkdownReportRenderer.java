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

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.model.Outcome;
import lombok.val;
import org.jspecify.annotations.Nullable;

/**
 * The human-readable rendering: a headline block (ended by {@value #HEADLINE_END},
 * which the Gradle plugin's {@code evaluateReport} task echoes to the console), then
 * one section per style bucket with a scenario table and, for every non-{@code
 * GROUNDED} step, both sides of the audit — the actor's claim, the judge's findings,
 * and the real tool trace.
 */
public class MarkdownReportRenderer implements EvaluationReportRenderer {

    /** Marks the end of the Markdown headline block. */
    public static final String HEADLINE_END = "<!-- headline-end -->";

    @Override
    public String fileName() {
        return "evaluation.md";
    }

    @Override
    public String render(EvaluationReport report) {
        val out = new StringBuilder("# Inquisitor evaluation report\n\n");
        appendHeadline(out, report);
        out.append('\n').append(HEADLINE_END).append('\n');
        for (val bucket : report.buckets()) {
            appendBucket(out, bucket);
        }
        return out.toString();
    }

    private static void appendHeadline(StringBuilder out, EvaluationReport report) {
        val totals = report.totals();
        out.append("- Generated: ").append(report.generatedAt())
                .append(" (took ").append(humanDuration(report.durationMillis())).append(")\n");
        if (report.header() != null) {
            out.append("- Header: ").append(report.header()).append('\n');
        }
        val runInfo = report.runInfo();
        if (runInfo != null) {
            out.append("- Actor: ").append(endpoint(runInfo.actorModel(), runInfo.actorBaseUrl())).append('\n');
            out.append("- Judge: ").append(endpoint(runInfo.judgeModel(), runInfo.judgeBaseUrl())).append('\n');
        }
        out.append("- Expectation gate: ").append(gate(totals)).append('\n');
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

    private static void appendBucket(StringBuilder out, EvaluationReport.Bucket bucket) {
        out.append("\n## ").append(bucket.name()).append('\n')
                .append("\n- Gate: ").append(gate(bucket.totals()))
                .append(" — Score: **").append(score(bucket.totals())).append("**\n\n")
                .append("| Scenario | Expected | Steps | Matched | Mean score |\n")
                .append("|---|---|---|---|---|\n");
        for (val scenario : bucket.scenarios()) {
            val evaluatedSteps = scenario.steps().stream().filter(StepEvaluationRecord::evaluated).toList();
            val mean = evaluatedSteps.isEmpty() ? Double.NaN : evaluatedSteps.stream()
                    .mapToDouble(StepEvaluationRecord::score).average().orElseThrow();
            out.append("| ").append(scenario.name())
                    .append(" | ").append(scenario.expectedOutcome())
                    .append(" | ").append(scenario.steps().size()).append('/')
                    .append(scenario.steps().getFirst().stepCount())
                    .append(" | ").append(scenario.matched() ? "yes" : missedLabel(scenario))
                    .append(" | ").append(Double.isNaN(mean) ? "—" : percent(mean))
                    .append(" |\n");
        }
        appendFindings(out, bucket);
    }

    private static String missedLabel(EvaluationReport.ScenarioReport scenario) {
        return scenario.expectedOutcome() == Outcome.FAIL ? "**MISSED DETECTION**" : "**no**";
    }

    private static void appendFindings(StringBuilder out, EvaluationReport.Bucket bucket) {
        val flagged = bucket.scenarios().stream()
                .flatMap(scenario -> scenario.steps().stream()
                        .filter(step -> step.evaluated() && step.score() < 1.0)
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
        return totals.scenariosMatched() + "/" + totals.scenarios() + " scenarios matched their expectation";
    }

    private static String score(EvaluationReport.Totals totals) {
        return totals.meanScore() == null ? "n/a" : percent(totals.meanScore());
    }

    private static String percent(double score) {
        return String.format(Locale.ROOT, "%.1f%%", score * 100.0);
    }

    private static String endpoint(@Nullable String model, @Nullable String baseUrl) {
        return (model == null ? "(unknown model)" : model) + (baseUrl == null ? "" : " @ " + baseUrl);
    }

    private static String humanDuration(long millis) {
        val duration = Duration.ofMillis(millis);
        val hours = duration.toHours();
        val minutes = duration.toMinutesPart();
        val seconds = duration.toSecondsPart();
        return hours > 0 ? "%dh %dm %ds".formatted(hours, minutes, seconds)
                : minutes > 0 ? "%dm %ds".formatted(minutes, seconds)
                : "%ds".formatted(seconds);
    }
}
