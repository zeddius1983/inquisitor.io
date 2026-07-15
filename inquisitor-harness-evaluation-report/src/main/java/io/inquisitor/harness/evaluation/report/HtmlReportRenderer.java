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

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.model.Outcome;
import lombok.val;

/**
 * A self-contained {@code evaluation.html}: the Gradle-test-report look with
 * <b>Evaluation rate next to Success rate</b> — the task-08 headline
 * ("100% passed / 85% grounded") as one page — and the same drill-down navigation as
 * the ordinary JUnit report, via in-page anchors: buckets overview → bucket table →
 * per-scenario section with its step table and expandable findings. Inline CSS, no
 * JavaScript (findings use native {@code <details>}); opens from the report directory
 * with no server.
 *
 * <p>Success rate is the actor's notion (PASS-outcome steps over recorded steps);
 * expectation semantics live in the Matched column, so a fault-detection row
 * deliberately reads "low success rate, matched" — that contrast is the information.
 *
 * <p>Every interpolated value goes through {@link #escape(String)}: reasoning,
 * evidence, feedback and the trace carry JSON/HTML-hostile characters from live
 * systems. See {@code tasks/task-13-html-report-renderer.md}.
 */
public class HtmlReportRenderer implements EvaluationReportRenderer {

    private static final String STYLE = """
            body { font-family: system-ui, sans-serif; margin: 2em auto; max-width: 70em; \
            padding: 0 1em; color: #303030; }
            h1 { font-size: 1.6em; } h2 { font-size: 1.25em; margin-top: 1.8em; }
            h3 { font-size: 1.05em; margin-top: 1.6em; }
            h3 small { color: #808080; font-weight: normal; }
            .meta { color: #606060; line-height: 1.5; }
            .tiles { display: flex; gap: 1em; flex-wrap: wrap; margin: 1.5em 0; }
            .tile { border: 1px solid #d0d0d0; border-radius: 4px; padding: 0.7em 1.3em; \
            text-align: center; background: #f7f7f7; }
            .tile .value { font-size: 1.5em; font-weight: bold; }
            .tile .label { color: #606060; font-size: 0.85em; }
            table { border-collapse: collapse; width: 100%; margin: 0.8em 0 1.2em; }
            th, td { border: 1px solid #d0d0d0; padding: 0.45em 0.8em; text-align: left; \
            font-size: 0.95em; }
            th { background: #f0f0f0; }
            a { color: #1564bf; text-decoration: none; } a:hover { text-decoration: underline; }
            .ok { color: #197f19; font-weight: bold; }
            .bad { color: #b60808; font-weight: bold; }
            .dim { color: #808080; }
            details { margin: 0.5em 0; border: 1px solid #e0d5b8; background: #fdf8ea; \
            border-radius: 4px; padding: 0.5em 0.9em; }
            summary { cursor: pointer; font-weight: bold; }
            pre { background: #f4f4f4; padding: 0.7em; overflow-x: auto; font-size: 0.85em; }
            .top { font-size: 0.85em; }
            """;

    @Override
    public String fileName() {
        return "evaluation.html";
    }

    @Override
    public String render(EvaluationReport report) {
        val out = new StringBuilder();
        out.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<title>Inquisitor evaluation report</title>\n<style>\n").append(STYLE)
                .append("</style>\n</head>\n<body id=\"top\">\n")
                .append("<h1>Inquisitor evaluation report</h1>\n");
        appendMeta(out, report);
        appendTiles(out, report);
        appendBucketsOverview(out, report);
        for (val bucket : report.buckets()) {
            appendBucket(out, bucket);
        }
        out.append("</body>\n</html>\n");
        return out.toString();
    }

    private static void appendMeta(StringBuilder out, EvaluationReport report) {
        out.append("<p class=\"meta\">Generated ")
                .append(escape(report.generatedAt().truncatedTo(ChronoUnit.SECONDS).toString()))
                .append(" (took ").append(ReportFormats.humanDuration(report.durationMillis()))
                .append(")");
        if (report.header() != null) {
            out.append("<br>Header: ").append(escape(report.header()));
        }
        val runInfo = report.runInfo();
        if (runInfo != null) {
            out.append("<br>Actor: ")
                    .append(escape(ReportFormats.endpoint(runInfo.actorModel(), runInfo.actorBaseUrl())))
                    .append("<br>Judge: ")
                    .append(escape(ReportFormats.endpoint(runInfo.judgeModel(), runInfo.judgeBaseUrl())));
        }
        out.append("</p>\n");
    }

    private static void appendTiles(StringBuilder out, EvaluationReport report) {
        val totals = report.totals();
        out.append("<div class=\"tiles\">\n");
        tile(out, totals.scenariosMatched() + "/" + totals.scenarios(), "matched expectation");
        tile(out, String.valueOf(totals.stepsRecorded()), "steps recorded");
        tile(out, totals.meanScore() == null ? "n/a" : ReportFormats.percent(totals.meanScore()),
                "evaluation score");
        if (totals.stepsNotEvaluated() > 0) {
            tile(out, String.valueOf(totals.stepsNotEvaluated()), "not evaluated");
        }
        tile(out, ReportFormats.humanDuration(report.durationMillis()), "duration");
        out.append("</div>\n");
    }

    private static void tile(StringBuilder out, String value, String label) {
        out.append("<div class=\"tile\"><div class=\"value\">").append(escape(value))
                .append("</div><div class=\"label\">").append(escape(label))
                .append("</div></div>\n");
    }

    /** The index-page view: one row per bucket, linking into the bucket sections. */
    private static void appendBucketsOverview(StringBuilder out, EvaluationReport report) {
        out.append("<table>\n<thead><tr><th>Bucket</th><th>Scenarios</th><th>Matched</th>")
                .append("<th>Success rate</th><th>Evaluation rate</th></tr></thead>\n<tbody>\n");
        for (val bucket : report.buckets()) {
            val steps = allSteps(bucket);
            out.append("<tr><td><a href=\"#").append(bucketId(bucket)).append("\">")
                    .append(escape(bucket.name())).append("</a></td><td>")
                    .append(bucket.totals().scenarios()).append("</td><td>")
                    .append(bucket.totals().scenariosMatched()).append('/')
                    .append(bucket.totals().scenarios()).append("</td><td>")
                    .append(ReportFormats.percent(ReportFormats.successRate(steps)))
                    .append("</td><td>").append(rate(steps)).append("</td></tr>\n");
        }
        out.append("</tbody>\n</table>\n");
    }

    private static void appendBucket(StringBuilder out, EvaluationReport.Bucket bucket) {
        out.append("<h2 id=\"").append(bucketId(bucket)).append("\">").append(escape(bucket.name()))
                .append("</h2>\n<p class=\"meta\">Gate: ").append(bucket.totals().scenariosMatched())
                .append('/').append(bucket.totals().scenarios())
                .append(" matched &mdash; Success rate: ")
                .append(ReportFormats.percent(ReportFormats.successRate(allSteps(bucket))))
                .append(" &mdash; Evaluation rate: ").append(rate(allSteps(bucket))).append("</p>\n")
                .append("<table>\n<thead><tr><th>Scenario</th><th>Expected</th><th>Steps</th>")
                .append("<th>Success rate</th><th>Evaluation rate</th><th>Matched</th></tr></thead>\n")
                .append("<tbody>\n");
        for (var index = 0; index < bucket.scenarios().size(); index++) {
            val scenario = bucket.scenarios().get(index);
            out.append("<tr><td><a href=\"#").append(scenarioId(bucket, index)).append("\">")
                    .append(escape(scenario.name())).append("</a></td><td>")
                    .append(scenario.expectedOutcome())
                    .append("</td><td>").append(scenario.steps().size()).append('/')
                    .append(scenario.steps().getFirst().stepCount())
                    .append("</td><td>")
                    .append(ReportFormats.percent(ReportFormats.successRate(scenario.steps())))
                    .append("</td><td>").append(rate(scenario.steps()))
                    .append("</td><td>").append(matchedCell(scenario)).append("</td></tr>\n");
        }
        out.append("</tbody>\n</table>\n");
        for (var index = 0; index < bucket.scenarios().size(); index++) {
            appendScenario(out, bucket, index);
        }
    }

    /** The class-page view: per-scenario step table plus its findings. */
    private static void appendScenario(StringBuilder out, EvaluationReport.Bucket bucket, int index) {
        val scenario = bucket.scenarios().get(index);
        out.append("<h3 id=\"").append(scenarioId(bucket, index)).append("\">")
                .append(escape(scenario.name()));
        if (scenario.source() != null) {
            out.append(" <small>").append(escape(scenario.source())).append("</small>");
        }
        out.append("</h3>\n<p class=\"meta\">Expected ").append(scenario.expectedOutcome())
                .append(" &mdash; ").append(scenario.matched()
                        ? "<span class=\"ok\">matched</span>"
                        : matchedCell(scenario))
                .append("</p>\n")
                .append("<table>\n<thead><tr><th>#</th><th>Step</th><th>Outcome</th>")
                .append("<th>Category</th><th>Score</th><th>Actor time</th></tr></thead>\n<tbody>\n");
        for (val step : scenario.steps()) {
            out.append("<tr><td>").append(step.stepIndex()).append('/').append(step.stepCount())
                    .append("</td><td>").append(escape(step.stepTitle()))
                    .append("</td><td>").append(outcomeCell(step.outcome()))
                    .append("</td><td>").append(step.category() == null
                            ? "<span class=\"dim\">&mdash;</span>"
                            : escape(step.category()))
                    .append("</td><td>").append(step.evaluated()
                            ? ReportFormats.percent(step.score())
                            : "<span class=\"dim\">&mdash;</span>")
                    .append("</td><td>").append(step.elapsedMillis()).append(" ms</td></tr>\n");
        }
        out.append("</tbody>\n</table>\n");
        for (val step : scenario.steps()) {
            appendFinding(out, step);
        }
        out.append("<p class=\"top\"><a href=\"#top\">&uarr; back to top</a></p>\n");
    }

    /** A finding: any step the judge scored below 1.0, or never scored (with the reason). */
    private static void appendFinding(StringBuilder out, StepEvaluationRecord step) {
        if (step.evaluated() && step.score() >= 1.0) {
            return;
        }
        out.append("<details><summary>Step ").append(step.stepIndex())
                .append(" “").append(escape(step.stepTitle())).append("” &mdash; ")
                .append(escape(String.valueOf(step.category()))).append("</summary>\n")
                .append("<p><b>Actor verdict:</b> ").append(step.outcome()).append(" &mdash; ")
                .append(escape(step.reasoning())).append("</p>\n");
        if (!step.evidence().isEmpty()) {
            out.append("<p><b>Actor evidence:</b> ")
                    .append(escape(String.join("; ", step.evidence()))).append("</p>\n");
        }
        if (!step.feedback().isBlank()) {
            out.append("<p><b>Judge:</b> ").append(escape(step.feedback())).append("</p>\n");
        }
        if (!step.toolCalls().isEmpty()) {
            out.append("<pre>").append(escape(String.join("\n", step.toolCalls()))).append("</pre>\n");
        }
        out.append("</details>\n");
    }

    private static List<StepEvaluationRecord> allSteps(EvaluationReport.Bucket bucket) {
        return bucket.scenarios().stream().flatMap(scenario -> scenario.steps().stream()).toList();
    }

    private static String rate(List<StepEvaluationRecord> steps) {
        val rate = ReportFormats.evaluationRate(steps);
        return Double.isNaN(rate) ? "<span class=\"dim\">&mdash;</span>" : ReportFormats.percent(rate);
    }

    private static String outcomeCell(Outcome outcome) {
        return outcome == Outcome.PASS
                ? "<span class=\"ok\">PASS</span>"
                : "<span class=\"bad\">FAIL</span>";
    }

    private static String matchedCell(EvaluationReport.ScenarioReport scenario) {
        if (scenario.matched()) {
            return "<span class=\"ok\">yes</span>";
        }
        return scenario.expectedOutcome() == Outcome.FAIL
                ? "<span class=\"bad\">MISSED DETECTION</span>"
                : "<span class=\"bad\">no</span>";
    }

    private static String bucketId(EvaluationReport.Bucket bucket) {
        return "bucket-" + slug(bucket.name());
    }

    private static String scenarioId(EvaluationReport.Bucket bucket, int index) {
        return "scenario-" + slug(bucket.name()) + "-" + index;
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
