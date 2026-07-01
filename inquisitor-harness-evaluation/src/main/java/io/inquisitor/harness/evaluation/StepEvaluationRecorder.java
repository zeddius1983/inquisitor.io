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

import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import lombok.val;
import org.springframework.ai.evaluation.EvaluationResponse;

/**
 * Collects per-step scores across a run for the report. The only state
 * shared across steps/scenarios; thread-safe so parallel scenarios can record freely.
 */
public class StepEvaluationRecorder {

    private final List<StepEvaluationRecord> records = new CopyOnWriteArrayList<>();

    /** Records the evaluator's result for one step. */
    public void record(Scenario scenario, Step step, EvaluationResponse response) {
        val metadata = response.getMetadata();
        val category = metadata != null && metadata.get("category") != null
                ? String.valueOf(metadata.get("category"))
                : null;
        records.add(new StepEvaluationRecord(
                scenario.name(), step.index(), step.title(),
                response.getScore(), category, response.getFeedback()));
    }

    /** Every recorded step result, in record order. */
    public List<StepEvaluationRecord> records() {
        return List.copyOf(records);
    }

    /** The mean score across all recorded steps, or empty if none. */
    public OptionalDouble overallScore() {
        return records.stream().mapToDouble(StepEvaluationRecord::score).average();
    }

    public void clear() {
        records.clear();
    }
}
