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

package io.inquisitor.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.inquisitor.demo.service.AccountServiceRouter;
import io.inquisitor.demo.service.Bug;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Oracle calibration via mutation testing: runs the <em>correct</em> positive
 * scenarios against a deliberately buggy build and asserts the model reports the
 * failure at the step where the seeded {@link Bug} manifests. A {@code FAIL} is the
 * success condition — it proves the oracle catches a real defect instead of
 * rubber-stamping it. See {@code tasks/task-07-fault-detection.md}.
 *
 * <p>The harness plumbing (target registration, parsing, and the optional evaluation
 * read-out) is shared with {@link ScenarioTests} through {@link AbstractScenarioTests};
 * this class only adds the bug injection and the fault-detection assertions.
 *
 * <p>Bugs are injected through the {@code @Primary} {@link AccountServiceRouter},
 * enabled per test and reset in {@link #clearBugs()}. Like the rest of the LLM suite
 * this is gated: run with {@code INQUISITOR_LLM_IT=true}.
 */
class FaultDetectionTests extends AbstractScenarioTests {

    @Autowired
    private AccountServiceRouter router;

    @AfterEach
    void clearBugs() {
        router.disableAllBugs();
    }

    @Test
    void catchesADepositThatNeverPersists() {
        runExpectingFailureAt(Bug.DEPOSIT_NOT_PERSISTED,
                "scenarios/explicit/open-account-and-deposit.md", "Deposit");
    }

    @Test
    void catchesATransferThatDropsTheCredit() {
        runExpectingFailureAt(Bug.TRANSFER_CREDIT_DROPPED,
                "scenarios/explicit/transfer-between-accounts.md", "Verify resulting balances");
    }

    @Test
    void catchesAnAccountOpenedInTheWrongCurrency() {
        runExpectingFailureAt(Bug.WRONG_CURRENCY,
                "scenarios/explicit/open-account-and-deposit.md", "Open an account");
    }

    private void runExpectingFailureAt(Bug bug, String classpathLocation, String expectedStepTitleFragment) {
        router.enableBug(bug);

        val scenario = parse(classpathLocation);
        val recordedBefore = recordedCount();
        val result = executor.execute(scenario);
        // The suite drives the same scenario under different bugs, so tag the read-out
        // with the seeded bug to keep the two open-account runs distinct.
        reportEvaluation("scenario '" + scenario.name() + "' under " + bug, recordedBefore);

        // The model must NOT have passed everything — that would be a false positive
        // (it failed to notice the seeded bug).
        val failure = result.firstFailure();
        assertThat(failure)
                .withFailMessage(() -> "Expected the model to catch " + bug + " at a step containing \""
                        + expectedStepTitleFragment + "\", but the scenario passed:\n" + describe(result))
                .isPresent();

        // …and it must fail at the step the bug manifests at, not somewhere upstream.
        assertThat(failure.get().step().title())
                .withFailMessage(() -> "Scenario failed at the wrong step:\n" + describe(result))
                .containsIgnoringCase(expectedStepTitleFragment);
    }
}
