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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.model.Outcome;
import lombok.Builder;
import lombok.val;
import org.jspecify.annotations.Nullable;

/**
 * The aggregated result of one evaluation run, grouped by style bucket (the parent
 * directory of the scenario source). Built from the flat record stream by
 * {@link #of(Instant, Duration, String, EvaluationRunInfo, List) of(...)}.
 *
 * <p>Two aggregation rules worth spelling out:
 * <ul>
 *   <li><b>Instances, not names.</b> Records are split into scenario <em>instances</em>
 *   by watching the step index reset — the same file runs several times in one JVM
 *   (positive suite and fault suite share the explicit bucket), and name + source alone
 *   would fold those runs together.</li>
 *   <li><b>The gate is expectation-aware.</b> A scenario matched its expectation when it
 *   completed with every step PASS (expected {@link Outcome#PASS}) or when some step
 *   FAILed (expected {@link Outcome#FAIL} — fault detection, where the failure is the
 *   success and a fully-green run is a <em>missed detection</em>).</li>
 * </ul>
 */
@Builder
public record EvaluationReport(
        Instant generatedAt,
        long durationMillis,
        @Nullable String header,
        @Nullable EvaluationRunInfo runInfo,
        Totals totals,
        List<Bucket> buckets) {

    /** Aggregate numbers for the whole run or one bucket. */
    @Builder
    public record Totals(
            int scenarios,
            int scenariosMatched,
            int stepsRecorded,
            int stepsEvaluated,
            int stepsNotEvaluated,
            @Nullable Double meanScore,
            Map<String, Integer> categories) {
    }

    /** One style bucket: the parent directory of the scenario sources. */
    public record Bucket(String name, Totals totals, List<ScenarioReport> scenarios) {
    }

    /** One run of one scenario file. */
    public record ScenarioReport(
            String name,
            @Nullable String source,
            Outcome expectedOutcome,
            boolean matched,
            List<StepEvaluationRecord> steps) {
    }

    /** Builds the grouped report from the flat, execution-ordered record stream. */
    public static EvaluationReport of(Instant generatedAt, Duration duration, @Nullable String header,
            @Nullable EvaluationRunInfo runInfo, List<StepEvaluationRecord> records) {
        val instances = splitIntoInstances(records);

        val byBucket = new LinkedHashMap<String, List<ScenarioReport>>();
        for (val instance : instances) {
            byBucket.computeIfAbsent(bucketOf(instance.source()), bucket -> new ArrayList<>()).add(instance);
        }
        val buckets = byBucket.entrySet().stream()
                .map(entry -> new Bucket(entry.getKey(), totalsOf(entry.getValue()), List.copyOf(entry.getValue())))
                .toList();

        return EvaluationReport.builder()
                .generatedAt(generatedAt)
                .durationMillis(duration.toMillis())
                .header(header)
                .runInfo(runInfo)
                .totals(totalsOf(instances))
                .buckets(buckets)
                .build();
    }

    private static List<ScenarioReport> splitIntoInstances(List<StepEvaluationRecord> records) {
        val instances = new ArrayList<ScenarioReport>();
        var current = new ArrayList<StepEvaluationRecord>();
        for (val record : records) {
            if (!current.isEmpty() && startsNewInstance(current.getLast(), record)) {
                instances.add(toScenarioReport(current));
                current = new ArrayList<>();
            }
            current.add(record);
        }
        if (!current.isEmpty()) {
            instances.add(toScenarioReport(current));
        }
        return instances;
    }

    private static boolean startsNewInstance(StepEvaluationRecord last, StepEvaluationRecord next) {
        return !last.scenario().equals(next.scenario())
                || !java.util.Objects.equals(last.scenarioSource(), next.scenarioSource())
                || next.stepIndex() <= last.stepIndex();
    }

    private static ScenarioReport toScenarioReport(List<StepEvaluationRecord> steps) {
        val first = steps.getFirst();
        val expected = first.expectedOutcome();
        val anyFailed = steps.stream().anyMatch(step -> step.outcome() == Outcome.FAIL);
        val complete = steps.getLast().stepIndex() == first.stepCount();
        val matched = expected == Outcome.FAIL ? anyFailed : !anyFailed && complete;
        return new ScenarioReport(first.scenario(), first.scenarioSource(), expected, matched,
                List.copyOf(steps));
    }

    private static Totals totalsOf(List<ScenarioReport> scenarios) {
        val steps = scenarios.stream().flatMap(scenario -> scenario.steps().stream()).toList();
        val evaluated = steps.stream().filter(StepEvaluationRecord::evaluated).toList();
        val meanScore = evaluated.isEmpty()
                ? null
                : Double.valueOf(evaluated.stream().mapToDouble(StepEvaluationRecord::score).average().orElseThrow());
        val categories = new LinkedHashMap<String, Integer>();
        for (val step : steps) {
            val category = step.category() == null ? "(uncategorized)" : step.category();
            categories.merge(category, 1, Integer::sum);
        }
        return Totals.builder()
                .scenarios(scenarios.size())
                .scenariosMatched((int) scenarios.stream().filter(ScenarioReport::matched).count())
                .stepsRecorded(steps.size())
                .stepsEvaluated(evaluated.size())
                .stepsNotEvaluated(steps.size() - evaluated.size())
                .meanScore(meanScore)
                .categories(categories)
                .build();
    }

    private static String bucketOf(@Nullable String source) {
        if (source == null) {
            return "(no source)";
        }
        // "classpath:scenarios/cucumber/x.md" → "cucumber"; a bare filename has no bucket.
        val path = source.substring(source.indexOf(':') + 1);
        val segments = path.split("/");
        return segments.length >= 2 ? segments[segments.length - 2] : "(no bucket)";
    }
}
