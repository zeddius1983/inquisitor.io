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
 * The aggregated result of one evaluation run, grouped by the scenario group — the
 * JUnit suite class when run through the JUnit layer (a suite may mix scenarios from
 * any style buckets), falling back to the parent directory of the scenario source for
 * standalone runs. Built from the flat record stream by
 * {@link #of(Instant, Duration, String, EvaluationRunInfo, List) of(...)}.
 *
 * <p>Two aggregation rules worth spelling out:
 * <ul>
 *   <li><b>Instances, not names.</b> Records are split into scenario <em>instances</em>
 *   by watching the group/source change and the step index reset — the same file runs
 *   several times in one JVM (positive suite and fault suite, even within one suite
 *   under different expectations), and name + source alone would fold those runs
 *   together.</li>
 *   <li><b>PASSED/FAILED is expectation-aware</b> — JUnit's reading. A scenario PASSED
 *   when it completed with every step PASS (expected {@link Outcome#PASS}) or when some
 *   step FAILed (expected {@link Outcome#FAIL} — fault detection, where the failure is
 *   the success and a fully-green run is a <em>missed detection</em>).</li>
 * </ul>
 */
@Builder
public record EvaluationReport(
        Instant generatedAt,
        long durationMillis,
        @Nullable String header,
        @Nullable EvaluationRunInfo runInfo,
        Totals totals,
        List<Group> groups) {

    /** Aggregate numbers for the whole run or one bucket. */
    @Builder
    public record Totals(
            int scenarios,
            int scenariosPassed,
            int stepsRecorded,
            int stepsEvaluated,
            int stepsNotEvaluated,
            @Nullable Double meanScore,
            Map<String, Integer> categories) {
    }

    /** One group: the suite class (JUnit layer) or the source directory (standalone). */
    public record Group(String name, Totals totals, List<ScenarioReport> scenarios) {
    }

    /** One run of one scenario file. */
    public record ScenarioReport(
            String name,
            @Nullable String source,
            Outcome expectedOutcome,
            boolean passed,
            List<StepEvaluationRecord> steps) {

        /** An expected failure that never happened — expectation-aware FAILED, flagged. */
        public boolean missedDetection() {
            return !passed && expectedOutcome == Outcome.FAIL;
        }
    }

    /** Builds the grouped report from the flat, execution-ordered record stream. */
    public static EvaluationReport of(Instant generatedAt, Duration duration, @Nullable String header,
            @Nullable EvaluationRunInfo runInfo, List<StepEvaluationRecord> records) {
        val instances = splitIntoInstances(records);

        val byGroup = new LinkedHashMap<String, List<GroupedInstance>>();
        for (val instance : instances) {
            byGroup.computeIfAbsent(instance.group(), group -> new ArrayList<>()).add(instance);
        }
        val groups = byGroup.entrySet().stream()
                .map(entry -> {
                    val scenarios = entry.getValue().stream().map(GroupedInstance::scenario).toList();
                    return new Group(entry.getKey(), totalsOf(scenarios), scenarios);
                })
                .toList();

        return EvaluationReport.builder()
                .generatedAt(generatedAt)
                .durationMillis(duration.toMillis())
                .header(header)
                .runInfo(runInfo)
                .totals(totalsOf(instances.stream().map(GroupedInstance::scenario).toList()))
                .groups(groups)
                .build();
    }

    /** A scenario instance paired with the group it ran in. */
    private record GroupedInstance(String group, ScenarioReport scenario) {
    }

    private static List<GroupedInstance> splitIntoInstances(List<StepEvaluationRecord> records) {
        val instances = new ArrayList<GroupedInstance>();
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
                || !java.util.Objects.equals(last.scenarioGroup(), next.scenarioGroup())
                || next.stepIndex() <= last.stepIndex();
    }

    private static GroupedInstance toScenarioReport(List<StepEvaluationRecord> steps) {
        val first = steps.getFirst();
        val expected = first.expectedOutcome();
        val anyFailed = steps.stream().anyMatch(step -> step.outcome() == Outcome.FAIL);
        val complete = steps.getLast().stepIndex() == first.stepCount();
        val passed = expected == Outcome.FAIL ? anyFailed : !anyFailed && complete;
        return new GroupedInstance(groupOf(first),
                new ScenarioReport(first.scenario(), first.scenarioSource(), expected, passed,
                        List.copyOf(steps)));
    }

    /** The suite class when the JUnit layer set it; the source directory standalone. */
    private static String groupOf(StepEvaluationRecord record) {
        if (record.scenarioGroup() != null) {
            return record.scenarioGroup();
        }
        return sourceDirectoryOf(record.scenarioSource());
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
                .scenariosPassed((int) scenarios.stream().filter(ScenarioReport::passed).count())
                .stepsRecorded(steps.size())
                .stepsEvaluated(evaluated.size())
                .stepsNotEvaluated(steps.size() - evaluated.size())
                .meanScore(meanScore)
                .categories(categories)
                .build();
    }

    private static String sourceDirectoryOf(@Nullable String source) {
        if (source == null) {
            return "(no source)";
        }
        // "classpath:scenarios/cucumber/x.md" → "cucumber"; a bare filename has no bucket.
        val path = source.substring(source.indexOf(':') + 1);
        val segments = path.split("/");
        return segments.length >= 2 ? segments[segments.length - 2] : "(no bucket)";
    }
}
