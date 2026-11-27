/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.inquisitor.harness.evaluation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.inquisitor.harness.executor.LlmStepRunner;
import io.inquisitor.harness.executor.StepAttempt;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.executor.StepRunner;
import io.inquisitor.harness.executor.UnresolvedStepException;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.StepVerdict;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.converter.BeanOutputConverter;

/** Bounded execution/refinement of a single step; never restarts the scenario. */
@Slf4j
public class SupervisedStepRunner implements StepRunner {
    private final LlmStepRunner actor;
    private final StepReviewer reviewer;
    private final int maxCorrections;
    private final EvaluationStepRunnerCallback callback;

    public SupervisedStepRunner(LlmStepRunner actor, StepReviewer reviewer, int maxCorrections,
            EvaluationStepRunnerCallback callback) {
        if (maxCorrections < 0) {
            throw new IllegalArgumentException("maxCorrections must be nonnegative");
        }
        this.actor = actor;
        this.reviewer = reviewer;
        this.maxCorrections = maxCorrections;
        this.callback = callback;
    }

    @Override
    public StepRun run(StepRequest request) {
        val conversation = actor.openConversation(request);
        val rounds = new ArrayList<StepReviewRound>();
        var feedback = "";
        for (int index = 0; index <= maxCorrections; index++) {
            StepAttempt attempt;
            try {
                attempt = index == 0 ? conversation.execute() : conversation.continueWith(feedback);
            } catch (RuntimeException exception) {
                val failed = conversation.attempts().getLast();
                rounds.add(new StepReviewRound(failed, null, 0, "Actor/tool call failed: " + exception.getMessage()));
                throw unresolved(request, conversation, rounds, "Actor/tool call failed; effects may be unknown.", exception);
            }
            StepReview review = null;
            var judgeMillis = 0L;
            if (attempt.verdict() == null) {
                feedback = "Your response does not match the verdict schema. Return a valid verdict, "
                        + "preserving established facts. Validation: "
                        + (attempt.error() == null ? "Empty or missing verdict" : attempt.error())
                        + "\n" + new BeanOutputConverter<>(StepVerdict.class).getFormat();
            } else {
                val started = System.nanoTime();
                try {
                    review = reviewer.review(request, attempt, conversation.toolCalls());
                    if (review == null) {
                        throw new IllegalStateException("The reviewer returned no decision");
                    }
                } catch (RuntimeException exception) {
                    judgeMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    rounds.add(new StepReviewRound(attempt, null, judgeMillis,
                            "Reviewer failed: " + exception.getMessage()));
                    throw unresolved(request, conversation, rounds, "The reviewer could not establish a decision.", exception);
                }
                judgeMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
                feedback = String.join("\n", review.findings()) + "\n" + review.feedback();
            }
            val round = new StepReviewRound(attempt, review, judgeMillis, feedback);
            rounds.add(round);
            notifyCallback(() -> callback.reviewCompleted(request, round));
            if (review != null && review.decision() == StepReview.Decision.ACCEPT) {
                val run = conversation.completeSupervised(Objects.requireNonNull(attempt.verdict()), false);
                notifyCallback(() -> callback.supervisionCompleted(request, run, List.copyOf(rounds), false, ""));
                return run;
            }
            if (review != null && review.decision() == StepReview.Decision.STOP) {
                throw unresolved(request, conversation, rounds, review.feedback(), null);
            }
            if (index == maxCorrections) {
                break;
            }
        }
        throw unresolved(request, conversation, rounds, "Correction budget exhausted without an accepted verdict.", null);
    }

    private UnresolvedStepException unresolved(StepRequest request, LlmStepRunner.Conversation conversation,
            List<StepReviewRound> rounds, String reason, @Nullable Throwable cause) {
        val exception = new UnresolvedStepException(reason, conversation.attempts(), cause);
        val run = conversation.snapshot(new StepVerdict(Outcome.FAIL, reason, List.of()), true);
        notifyCallback(() -> callback.supervisionCompleted(request, run, List.copyOf(rounds), true, reason));
        conversation.abort(exception);
        return exception;
    }

    private static void notifyCallback(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException exception) {
            log.warn("Supervision callback failed", exception);
        }
    }
}
