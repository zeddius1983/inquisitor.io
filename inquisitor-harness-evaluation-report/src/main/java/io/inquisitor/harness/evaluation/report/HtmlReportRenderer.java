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
 * {@code evaluation.html} (summary tiles + groups overview) → {@code groups/*.html}
 * (one scenario table per group — the JUnit suite, or the source directory standalone) →
 * {@code scenarios/*.html} (per-scenario step table
 * and findings), breadcrumbs on every page. Its headline column pair is
 * <b>Evaluation score next to Success rate</b>: the task-08 headline
 * ("100% passed / 85% grounded") as one view.
 *
 * <p><b>Result and success rate are expectation-aware — JUnit's reading.</b> A
 * fault-detection scenario ({@code Expected FAIL}) that fails is {@code PASSED}; one
 * that stays green is {@code FAILED (missed detection)}. The raw actor verdicts stay
 * visible per step on the scenario page, where a fault run legitimately shows FAIL
 * outcomes under a PASSED result. Findings include never-scored steps
 * ({@code NOT_EVALUATED}) with their reason.
 *
 * <p>Pages are self-contained (inline CSS, no JavaScript, native {@code <details>});
 * every interpolated value goes through {@link #escape(String)} — reasoning, evidence,
 * feedback and the trace carry JSON/HTML-hostile characters from live systems.
 */
public class HtmlReportRenderer implements EvaluationReportRenderer {

    private static final String STYLE = """
            :root { --surface: #fcfcfb; --page: #f5f5f2; --ink: #0b0b0b; --ink-2: #52514e; \
            --ink-3: #8a8984; --line: #e6e5e0; --line-soft: #edece7; --accent: #2a78d6; \
            --good-ink: #0a6b0a; --good-bg: #e6f4e6; --bad-ink: #b02a2a; --bad-bg: #fbe9e9; }
            * { box-sizing: border-box; }
            body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; \
            margin: 0 auto; max-width: 72em; padding: 2.5em 1.5em 3em; color: var(--ink); \
            background: var(--page); line-height: 1.55; }
            h1 { font-size: 1.5em; font-weight: 650; letter-spacing: -0.01em; margin: 0 0 0.4em; }
            h1 small { color: var(--ink-3); font-weight: 400; font-size: 0.6em; letter-spacing: 0; }
            .breadcrumb { color: var(--ink-3); font-size: 0.85em; margin: 0 0 1.6em; }
            .meta { color: var(--ink-2); font-size: 0.9em; line-height: 1.7; margin: 0.4em 0 1em; }
            .tiles { display: flex; gap: 0.9em; flex-wrap: wrap; margin: 1.6em 0; }
            .tile { background: var(--surface); border: 1px solid var(--line); \
            border-radius: 12px; padding: 0.9em 1.4em; min-width: 8em; \
            box-shadow: 0 1px 2px rgba(0,0,0,0.04), 0 4px 14px rgba(0,0,0,0.03); }
            .tile .value { font-size: 1.55em; font-weight: 650; letter-spacing: -0.01em; }
            .tile .label { color: var(--ink-2); font-size: 0.8em; margin-top: 0.15em; }
            table { border-collapse: separate; border-spacing: 0; width: 100%; \
            margin: 1em 0 1.6em; background: var(--surface); border: 1px solid var(--line); \
            border-radius: 12px; overflow: hidden; font-size: 0.92em; \
            box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
            th { text-align: left; font-size: 0.78em; font-weight: 600; \
            text-transform: uppercase; letter-spacing: 0.07em; color: var(--ink-2); \
            background: #f3f2ee; padding: 0.7em 1em; border-bottom: 1px solid var(--line); }
            td { padding: 0.6em 1em; border-bottom: 1px solid var(--line-soft); \
            font-variant-numeric: tabular-nums; }
            tbody tr:last-child td { border-bottom: none; }
            tbody tr:hover { background: #f6f8fc; }
            a { color: var(--accent); text-decoration: none; }
            a:hover { text-decoration: underline; }
            .ok, .bad { display: inline-block; border-radius: 999px; padding: 0.1em 0.7em; \
            font-size: 0.85em; font-weight: 600; }
            .ok { color: var(--good-ink); background: var(--good-bg); }
            .bad { color: var(--bad-ink); background: var(--bad-bg); }
            .dim { color: var(--ink-3); }
            details { margin: 0.7em 0; border: 1px solid #ecdfb4; background: #fffbee; \
            border-radius: 10px; padding: 0.6em 1em; }
            details[open] { padding-bottom: 0.9em; }
            summary { cursor: pointer; font-weight: 600; font-size: 0.92em; }
            summary:hover { color: var(--accent); }
            details p { margin: 0.5em 0; font-size: 0.9em; }
            pre { background: #f2f1ed; border: 1px solid var(--line); border-radius: 8px; \
            padding: 0.8em 1em; overflow-x: auto; font-size: 0.82em; line-height: 1.5; \
            font-family: ui-monospace, "Cascadia Code", Menlo, Consolas, monospace; }
            .top { font-size: 0.85em; }
            """;

    @Override
    public String name() {
        return "html";
    }

    @Override
    public List<Path> render(EvaluationReport report, Path dir) {
        try {
            Files.createDirectories(dir.resolve("groups"));
            Files.createDirectories(dir.resolve("scenarios"));
            val files = new ArrayList<Path>();
            files.add(write(dir.resolve("evaluation.html"), indexPage(report)));
            for (val group : report.groups()) {
                files.add(write(dir.resolve("groups/" + slug(group.name()) + ".html"),
                        groupPage(group)));
                for (var index = 0; index < group.scenarios().size(); index++) {
                    files.add(write(dir.resolve("scenarios/" + scenarioFile(group, index)),
                            scenarioPage(group, index)));
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
        body.append("<table>\n<thead><tr><th>Group</th><th>Scenarios</th><th>Passed</th>")
                .append("<th>Success rate</th><th>Evaluation score</th></tr></thead>\n<tbody>\n");
        for (val group : report.groups()) {
            body.append("<tr><td><a href=\"groups/").append(slug(group.name())).append(".html\">")
                    .append(escape(group.name())).append("</a></td><td>")
                    .append(group.totals().scenarios()).append("</td><td>")
                    .append(group.totals().scenariosPassed()).append('/')
                    .append(group.totals().scenarios()).append("</td><td>")
                    .append(successRate(group.totals()))
                    .append("</td><td>").append(score(allSteps(group))).append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        return page("Inquisitor evaluation report", null, body.toString());
    }

    private static String groupPage(EvaluationReport.Group group) {
        val body = new StringBuilder("<h1>").append(escape(group.name())).append("</h1>\n")
                .append("<p class=\"meta\">Passed: ").append(group.totals().scenariosPassed())
                .append('/').append(group.totals().scenarios())
                .append(" (success rate ").append(successRate(group.totals()))
                .append(") &mdash; Evaluation score: ").append(score(allSteps(group))).append("</p>\n")
                .append("<table>\n<thead><tr><th>Scenario</th><th>Expected</th><th>Steps</th>")
                .append("<th>Result</th><th>Evaluation score</th></tr></thead>\n")
                .append("<tbody>\n");
        for (var index = 0; index < group.scenarios().size(); index++) {
            val scenario = group.scenarios().get(index);
            body.append("<tr><td><a href=\"../scenarios/").append(scenarioFile(group, index))
                    .append("\">").append(escape(scenario.name())).append("</a></td><td>")
                    .append(scenario.expectedOutcome())
                    .append("</td><td>").append(scenario.steps().size()).append('/')
                    .append(scenario.steps().getFirst().stepCount())
                    .append("</td><td>").append(resultCell(scenario))
                    .append("</td><td>").append(score(scenario.steps()))
                    .append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        val breadcrumb = "<a href=\"../evaluation.html\">report</a> &rsaquo; " + escape(group.name());
        return page(group.name(), breadcrumb, body.toString());
    }

    private static String scenarioPage(EvaluationReport.Group group, int index) {
        val scenario = group.scenarios().get(index);
        val body = new StringBuilder("<h1>").append(escape(scenario.name()));
        if (scenario.source() != null) {
            body.append(" <small>").append(escape(scenario.source())).append("</small>");
        }
        body.append("</h1>\n<p class=\"meta\">Expected ").append(scenario.expectedOutcome())
                .append(" &mdash; ").append(resultCell(scenario))
                .append(" &mdash; Evaluation score: ").append(score(scenario.steps())).append("</p>\n")
                .append("<table>\n<thead><tr><th>#</th><th>Step</th><th>Outcome</th>")
                .append("<th>Category</th><th>Score</th><th>Actor time</th></tr></thead>\n<tbody>\n");
        for (val step : scenario.steps()) {
            body.append("<tr><td>").append(step.stepIndex()).append('/').append(step.stepCount())
                    .append("</td><td>").append(escape(step.stepTitle()))
                    .append("</td><td>").append(outcomeCell(step.outcome()))
                    .append("</td><td>").append(categoryCell(step))
                    .append("</td><td>").append(step.evaluated()
                            ? colored(step.score(), ReportFormats.percent(step.score()))
                            : "<span class=\"dim\">&mdash;</span>")
                    .append("</td><td>").append(ReportFormats.humanMillis(step.elapsedMillis()))
                    .append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>\n");
        for (val step : scenario.steps()) {
            appendFinding(body, step);
        }
        val breadcrumb = "<a href=\"../evaluation.html\">report</a> &rsaquo; <a href=\"../groups/"
                + slug(group.name()) + ".html\">" + escape(group.name()) + "</a> &rsaquo; "
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
        tile(out, totals.scenariosPassed() + "/" + totals.scenarios(), "scenarios passed");
        tile(out, String.valueOf(totals.stepsRecorded()), "steps recorded");
        rawTile(out, totals.meanScore() == null
                ? "n/a"
                : colored(totals.meanScore(), ReportFormats.percent(totals.meanScore())),
                "evaluation score");
        if (totals.stepsNotEvaluated() > 0) {
            tile(out, String.valueOf(totals.stepsNotEvaluated()), "not evaluated");
        }
        tile(out, ReportFormats.humanDuration(report.durationMillis()), "duration");
        out.append("</div>\n");
    }

    private static void tile(StringBuilder out, String value, String label) {
        rawTile(out, escape(value), label);
    }

    private static void rawTile(StringBuilder out, String valueHtml, String label) {
        out.append("<div class=\"tile\"><div class=\"value\">").append(valueHtml)
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

    private static List<StepEvaluationRecord> allSteps(EvaluationReport.Group group) {
        return group.scenarios().stream().flatMap(scenario -> scenario.steps().stream()).toList();
    }

    private static String score(List<StepEvaluationRecord> steps) {
        val score = ReportFormats.evaluationScore(steps);
        return Double.isNaN(score)
                ? "<span class=\"dim\">&mdash;</span>"
                : colored(score, ReportFormats.percent(score));
    }

    /** Green (1.0) → amber (0.5) → red (0.0), interpolated on the hue axis. */
    private static String colored(double score, String text) {
        val hue = (int) Math.round(Math.clamp(score, 0.0, 1.0) * 120);
        return "<span style=\"color:hsl(" + hue + ",70%,33%)\">" + escape(text) + "</span>";
    }

    /** The category tinted by the score its classification maps to. */
    private static String categoryCell(StepEvaluationRecord step) {
        val category = step.category();
        if (category == null) {
            return "<span class=\"dim\">&mdash;</span>";
        }
        return switch (category) {
            case "GROUNDED" -> colored(1.0, category);
            case "PARTIALLY_GROUNDED" -> colored(0.5, category);
            case "UNSUPPORTED" -> colored(0.2, category);
            case "CONTRADICTED" -> colored(0.0, category);
            case StepEvaluationRecord.NOT_EVALUATED -> "<span class=\"dim\">" + category + "</span>";
            default -> escape(category);
        };
    }

    private static String outcomeCell(Outcome outcome) {
        return outcome == Outcome.PASS
                ? "<span class=\"ok\">PASS</span>"
                : "<span class=\"bad\">FAIL</span>";
    }

    private static String resultCell(EvaluationReport.ScenarioReport scenario) {
        if (scenario.passed()) {
            return "<span class=\"ok\">PASSED</span>";
        }
        return scenario.missedDetection()
                ? "<span class=\"bad\">FAILED (missed detection)</span>"
                : "<span class=\"bad\">FAILED</span>";
    }

    /** Expectation-aware, JUnit's reading: PASSED scenarios over scenarios. */
    private static String successRate(EvaluationReport.Totals totals) {
        return totals.scenarios() == 0 ? "&mdash;"
                : ReportFormats.percent(totals.scenariosPassed() / (double) totals.scenarios());
    }

    private static String scenarioFile(EvaluationReport.Group group, int index) {
        return slug(group.name()) + "-" + index + ".html";
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
