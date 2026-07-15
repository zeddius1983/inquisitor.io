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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.model.Outcome;
import lombok.val;

/**
 * The default report: multi-page HTML with the Gradle-test-report structure —
 * {@code evaluation.html} (summary tiles + buckets overview) → {@code buckets/*.html}
 * (one scenario table per bucket) → {@code scenarios/*.html} (per-scenario step table
 * and findings), breadcrumbs on every page. Its headline column pair is
 * <b>Evaluation score next to Success rate</b>: the task-08 headline
 * ("100% passed / 85% grounded") as one view.
 *
 * <p>Success rate is the actor's notion (PASS-outcome steps over recorded steps);
 * expectation semantics live in the Matched column, so a fault-detection row
 * deliberately reads "low success rate, matched" — that contrast is the information.
 * Findings include never-scored steps ({@code NOT_EVALUATED}) with their reason.
 *
 * <p>Pages are self-contained (inline CSS, no JavaScript, native {@code <details>});
 * every interpolated value goes through {@link #escape(String)} — reasoning, evidence,
 * feedback and the trace carry JSON/HTML-hostile characters from live systems.
 */
public class HtmlReportRenderer implements EvaluationReportRenderer {

    private static final String STYLE = """
            body { font-family: system-ui, sans-serif; margin: 2em auto; max-width: 70em; \
            padding: 0 1em; color: #303030; }
            h1 { font-size: 1.6em; }
            h1 small { color: #808080; font-weight: normal; font-size: 0.6em; }
            .breadcrumb { color: #808080; font-size: 0.9em; margin-bottom: 1.2em; }
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
            """;

    @Override
    public String name() {
        return "html";
    }

    @Override
    public List<Path> render(EvaluationReport report, Path dir) {
        try {
            Files.createDirectories(dir.resolve("buckets"));
            Files.createDirectories(dir.resolve("scenarios"));
            val files = new ArrayList<Path>();
            files.add(write(dir.resolve("evaluation.html"), indexPage(report)));
            for (val bucket : report.buckets()) {
                files.add(write(dir.resolve("buckets/" + slug(bucket.name()) + ".html"),
                        bucketPage(bucket)));
                for (var index = 0; index < bucket.scenarios().size(); index++) {
                    files.add(write(dir.resolve("scenarios/" + scenarioFile(bucket, index)),
                            scenarioPage(bucket, index)));
                }
            }
            return List.copyOf(files);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the HTML evaluation report to " + dir, e);
        }
    }

    private static Path write(Path file, String content) throws IOException {
        Files.writeString(file, content);
        return file;
    }

    // ---- pages ----

    private static String indexPage(EvaluationReport report) {
        val body = new StringBuilder("<h1>Inquisitor evaluation report</h1>\n");
        appendMeta(body, report);
        appendTiles(body, report);
        body.append("<table>\n<thead><tr><th>Bucket</th><th>Scenarios</th><th>Matched</th>")
                .append("<th>Success rate</th><th>Evaluation score</th></tr></thead>\n<tbody>\n");
        for (val bucket : report.buckets()) {
            val steps = allSteps(bucket);
            body.append("<tr><td><a href=\"buckets/").append(slug(bucket.name())).append(".html\">")
                    .append(escape(bucket.name())).append("</a></td><td>")
                    .append(bucket.totals().scenarios()).append("</td><td>")
                    .append(bucket.totals().scenariosMatched()).append('/')
                    .append(bucket.totals().scenarios()).append("</td><td>")
                    .append(ReportFormats.percent(ReportFormats.successRate(steps)))
                    .append("</td><td>").append(score(steps)).append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        return page("Inquisitor evaluation report", null, body.toString());
    }

    private static String bucketPage(EvaluationReport.Bucket bucket) {
        val body = new StringBuilder("<h1>").append(escape(bucket.name())).append("</h1>\n")
                .append("<p class=\"meta\">Gate: ").append(bucket.totals().scenariosMatched())
                .append('/').append(bucket.totals().scenarios())
                .append(" matched &mdash; Success rate: ")
                .append(ReportFormats.percent(ReportFormats.successRate(allSteps(bucket))))
                .append(" &mdash; Evaluation score: ").append(score(allSteps(bucket))).append("</p>\n")
                .append("<table>\n<thead><tr><th>Scenario</th><th>Expected</th><th>Steps</th>")
                .append("<th>Success rate</th><th>Evaluation score</th><th>Matched</th></tr></thead>\n")
                .append("<tbody>\n");
        for (var index = 0; index < bucket.scenarios().size(); index++) {
            val scenario = bucket.scenarios().get(index);
            body.append("<tr><td><a href=\"../scenarios/").append(scenarioFile(bucket, index))
                    .append("\">").append(escape(scenario.name())).append("</a></td><td>")
                    .append(scenario.expectedOutcome())
                    .append("</td><td>").append(scenario.steps().size()).append('/')
                    .append(scenario.steps().getFirst().stepCount())
                    .append("</td><td>")
                    .append(ReportFormats.percent(ReportFormats.successRate(scenario.steps())))
                    .append("</td><td>").append(score(scenario.steps()))
                    .append("</td><td>").append(matchedCell(scenario)).append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        val breadcrumb = "<a href=\"../evaluation.html\">report</a> &rsaquo; " + escape(bucket.name());
        return page(bucket.name(), breadcrumb, body.toString());
    }

    private static String scenarioPage(EvaluationReport.Bucket bucket, int index) {
        val scenario = bucket.scenarios().get(index);
        val body = new StringBuilder("<h1>").append(escape(scenario.name()));
        if (scenario.source() != null) {
            body.append(" <small>").append(escape(scenario.source())).append("</small>");
        }
        body.append("</h1>\n<p class=\"meta\">Expected ").append(scenario.expectedOutcome())
                .append(" &mdash; ").append(matchedCell(scenario))
                .append(" &mdash; Evaluation score: ").append(score(scenario.steps())).append("</p>\n")
                .append("<table>\n<thead><tr><th>#</th><th>Step</th><th>Outcome</th>")
                .append("<th>Category</th><th>Score</th><th>Actor time</th></tr></thead>\n<tbody>\n");
        for (val step : scenario.steps()) {
            body.append("<tr><td>").append(step.stepIndex()).append('/').append(step.stepCount())
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
        body.append("</tbody>\n</table>\n");
        for (val step : scenario.steps()) {
            appendFinding(body, step);
        }
        val breadcrumb = "<a href=\"../evaluation.html\">report</a> &rsaquo; <a href=\"../buckets/"
                + slug(bucket.name()) + ".html\">" + escape(bucket.name()) + "</a> &rsaquo; "
                + escape(scenario.name());
        return page(scenario.name(), breadcrumb, body.toString());
    }

    // ---- fragments ----

    private static String page(String title, String breadcrumb, String body) {
        val out = new StringBuilder();
        out.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<title>").append(escape(title)).append("</title>\n<style>\n").append(STYLE)
                .append("</style>\n</head>\n<body>\n");
        if (breadcrumb != null) {
            out.append("<p class=\"breadcrumb\">").append(breadcrumb).append("</p>\n");
        }
        out.append(body).append("</body>\n</html>\n");
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

    private static String score(List<StepEvaluationRecord> steps) {
        val score = ReportFormats.evaluationScore(steps);
        return Double.isNaN(score) ? "<span class=\"dim\">&mdash;</span>" : ReportFormats.percent(score);
    }

    private static String outcomeCell(Outcome outcome) {
        return outcome == Outcome.PASS
                ? "<span class=\"ok\">PASS</span>"
                : "<span class=\"bad\">FAIL</span>";
    }

    private static String matchedCell(EvaluationReport.ScenarioReport scenario) {
        if (scenario.matched()) {
            return "<span class=\"ok\">matched</span>";
        }
        return scenario.expectedOutcome() == Outcome.FAIL
                ? "<span class=\"bad\">MISSED DETECTION</span>"
                : "<span class=\"bad\">not matched</span>";
    }

    private static String scenarioFile(EvaluationReport.Bucket bucket, int index) {
        return slug(bucket.name()) + "-" + index + ".html";
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
