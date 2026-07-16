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

package io.inquisitor.harness.evaluation;

import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.CopyOnWriteArrayList;

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.model.ToolCallRecord;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.evaluation.EvaluationResponse;

/**
 * The shared output sink for step evaluations — the only cross-step shared state.
 * Collects one {@link StepEvaluationRecord} per evaluated step, in execution order, and
 * aggregates the suite-level evaluation score. Thread-safe.
 */
public class StepEvaluationRecorder {

    private final List<StepEvaluationRecord> records = new CopyOnWriteArrayList<>();

    /** Records the evaluator's result for one step. */
    public void record(StepRequest request, StepRun run, EvaluationResponse response) {
        val metadata = response.getMetadata();
        val category = metadata != null && metadata.get("category") != null
                ? String.valueOf(metadata.get("category"))
                : null;
        records.add(build(request, run, response.getScore(), category, response.getFeedback()));
    }

    /**
     * Records a step the judge never scored — a harness-synthesized verdict, or a failed
     * judge call — with the reason as the feedback.
     */
    public void recordNotEvaluated(StepRequest request, StepRun run, String reason) {
        records.add(build(request, run, 0.0, StepEvaluationRecord.NOT_EVALUATED, reason));
    }

    private static StepEvaluationRecord build(StepRequest request, StepRun run,
            double score, @Nullable String category, String feedback) {
        val scenario = request.scenario();
        val step = request.step();
        val verdict = run.verdict();
        return StepEvaluationRecord.builder()
                .scenario(scenario.name())
                .scenarioSource(scenario.source())
                .scenarioGroup(scenario.group())
                .expectedOutcome(scenario.expectedOutcome())
                .stepIndex(step.index())
                .stepCount(scenario.steps().size())
                .stepTitle(step.title())
                .outcome(verdict.outcome())
                .reasoning(verdict.reasoning())
                .evidence(verdict.evidence())
                .toolCalls(run.toolCalls().stream().map(ToolCallRecord::describe).toList())
                .elapsedMillis(run.elapsed().toMillis())
                .score(score)
                .category(category)
                .feedback(feedback)
                .build();
    }

    /** Every recorded step result, in record order. */
    public List<StepEvaluationRecord> records() {
        return List.copyOf(records);
    }

    /**
     * The mean score across all judged steps ({@code NOT_EVALUATED} records are
     * excluded), or empty if none.
     */
    public OptionalDouble overallScore() {
        return records.stream()
                .filter(StepEvaluationRecord::evaluated)
                .mapToDouble(StepEvaluationRecord::score)
                .average();
    }

    public void clear() {
        records.clear();
    }
}
