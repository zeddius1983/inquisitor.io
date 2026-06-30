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

import java.util.Map;
import java.util.stream.Collectors;

import lombok.val;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * A fact-checking-style {@link Evaluator} that judges whether a step's verdict is earned.
 *
 * <p>It audits the actor's claim ({@link EvaluationRequest#getResponseContent()}) against
 * the <em>real</em> tool-call trace ({@link EvaluationRequest#getDataList()} — never the
 * conversation), using a separate judge model, and classifies the result as a
 * {@link Credibility} category mapped to a 0.0–1.0 score.
 *
 * <p>The trace is the sole ground truth and is treated as complete, so a claimed action
 * absent from it is caught as fabrication. The judge runs with no tools; it rules on the
 * trace alone.
 */
public class CredibilityEvaluator implements Evaluator {

    /**
     * Built by string substitution rather than a {@code PromptTemplate} on purpose: the
     * trace and verdict contain literal braces (JSON), which a template engine would try
     * to interpret. Placeholders are unique tokens replaced literally.
     */
    private static final String PROMPT = """
            You are a test-trace auditor. A test agent performed ONE step against an
            application and reported a verdict. Your job is NOT to re-run or re-judge the
            step — it is to decide whether the agent's reported verdict is SUPPORTED by
            what the tools actually did.

            The TOOL TRACE below is the SOLE ground truth. It is the COMPLETE record of
            every tool call the agent made during this step and exactly what each returned.
            If an action is not in the trace, it did not happen. The agent's REASONING and
            EVIDENCE are CLAIMS to be verified against the trace — they are never evidence
            in themselves.

            STEP INSTRUCTION:
            <<userText>>

            TOOL TRACE (ground truth — every call made and its real result):
            <<context>>

            AGENT'S REPORTED VERDICT (the claim you must audit):
            <<response>>

            Audit the claim against the trace. Look for, in order of severity:
              1. CONTRADICTION — a cited status, id, count, amount or action that the trace
                 contradicts, or a claimed action with no matching tool call.
              2. MISSING WORK — an action the step required (a write, an import, a re-read
                 to verify the result) that is absent from the trace.
              3. UNSUPPORTED VERDICT — PASS/FAIL asserted without a tool result that
                 entails it (e.g. "the balance is correct" with no query that read it).

            Classify the verdict as exactly one of:
              GROUNDED            — every action and value it relies on is in, and consistent
                                    with, the trace.
              PARTIALLY_GROUNDED  — plausible, but a cited value or required action is missing.
              UNSUPPORTED         — the verdict is not entailed by any tool result.
              CONTRADICTED        — the trace contradicts it, or a claimed action never happened.

            Provide the category and one short finding per issue (the claim, and what the
            trace shows).
            """;

    private final ChatClient judge;

    public CredibilityEvaluator(ChatClient judge) {
        this.judge = judge;
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        val verdict = judge.prompt()
                .user(buildPrompt(request))
                .call()
                .entity(CredibilityVerdict.class);

        if (verdict == null || verdict.category() == null) {
            return new EvaluationResponse(false, 0.0f, "The evaluator returned no verdict.", Map.of());
        }
        val category = verdict.category();
        val feedback = verdict.findings().isEmpty() ? "" : String.join("; ", verdict.findings());
        return new EvaluationResponse(
                category == Credibility.GROUNDED,
                (float) category.score(),
                feedback,
                Map.of("category", category.name()));
    }

    private static String buildPrompt(EvaluationRequest request) {
        val trace = request.getDataList().isEmpty()
                ? "(no tool calls were made during this step)"
                : request.getDataList().stream().map(Document::getText).collect(Collectors.joining("\n"));
        return PROMPT
                .replace("<<userText>>", request.getUserText())
                .replace("<<context>>", trace)
                .replace("<<response>>", request.getResponseContent());
    }
}
