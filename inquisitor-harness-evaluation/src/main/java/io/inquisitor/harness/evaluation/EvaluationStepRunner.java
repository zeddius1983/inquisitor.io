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
import java.util.stream.Collectors;

import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.executor.StepRunner;
import io.inquisitor.harness.model.StepVerdict;
import io.inquisitor.harness.model.ToolCallRecord;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * A {@link StepRunner} decorator that scores how well-grounded each step's verdict is.
 *
 * <p>It runs the delegate, then has an {@link Evaluator} audit the verdict against the
 * real tool-call trace from the {@link StepRun} and records the score. The decorator is
 * transparent to the verdict — it returns the delegate's {@link StepRun} unchanged and
 * only observes — so enabling evaluation never changes whether a step passes.
 */
@Slf4j
public class EvaluationStepRunner implements StepRunner {

    private final StepRunner delegate;
    private final Evaluator evaluator;
    private final StepEvaluationRecorder recorder;

    public EvaluationStepRunner(StepRunner delegate, Evaluator evaluator, StepEvaluationRecorder recorder) {
        this.delegate = delegate;
        this.evaluator = evaluator;
        this.recorder = recorder;
    }

    @Override
    public StepRun run(StepRequest request) {
        val run = delegate.run(request);

        val scenario = request.scenario();
        val step = request.step();
        if (run.synthetic()) {
            // The harness fabricated this verdict (empty/unparseable model response) —
            // there is no actor claim to audit, so judging it would only produce noise.
            log.debug("[{}] step {}/{} - NOT_EVALUATED: harness-synthesized verdict",
                    scenario.name(), step.index(), scenario.steps().size());
            recorder.recordNotEvaluated(request, run,
                    "Harness-synthesized verdict (no actor claim to audit); not evaluated.");
            return run;
        }
        log.debug("[{}] step {}/{} - EVALUATE: {}",
                scenario.name(), step.index(), scenario.steps().size(), step.title());

        val context = run.toolCalls().isEmpty()
                ? List.<Document>of()
                : List.of(new Document(renderTrace(run.toolCalls())));
        EvaluationResponse evaluation;
        try {
            evaluation = evaluator.evaluate(
                    new EvaluationRequest(request.userMessage(), context, renderVerdict(run.verdict())));
        } catch (RuntimeException e) {
            // The judge is an observer: its infrastructure failures (timeouts, transport
            // errors) must never fail the actor's step. Record the gap and move on.
            log.warn("[{}] step {}/{} - NOT_EVALUATED: the judge call failed",
                    scenario.name(), step.index(), scenario.steps().size(), e);
            recorder.recordNotEvaluated(request, run,
                    "The judge call failed (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                            + "); not evaluated.");
            return run;
        }
        recorder.record(request, run, evaluation);

        val category = evaluation.getMetadata() == null ? null : evaluation.getMetadata().get("category");
        log.debug("[{}] step {}/{} - {}: score {}",
                scenario.name(), step.index(), scenario.steps().size(),
                category, evaluation.getScore());
        return run;
    }

    private static String renderTrace(List<ToolCallRecord> toolCalls) {
        return toolCalls.stream().map(ToolCallRecord::describe).collect(Collectors.joining("\n"));
    }

    private static String renderVerdict(StepVerdict verdict) {
        val evidence = verdict.evidence().isEmpty() ? "(none)" : String.join("\n", verdict.evidence());
        return "Outcome: " + verdict.outcome()
                + "\nReasoning: " + verdict.reasoning()
                + "\nEvidence:\n" + evidence;
    }
}
