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

import static io.inquisitor.harness.junit.Expect.FAIL;

import io.inquisitor.demo.junit.EnableBug;
import io.inquisitor.demo.service.Bug;
import io.inquisitor.harness.junit.Harness;
import io.inquisitor.harness.junit.RequiresLlm;
import io.inquisitor.harness.junit.Scenario;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Fault detection through the ergonomic {@code @Harness} layer — the Phase 2
 * counterpart to the standalone {@link FaultDetectionTests}. Each method enables a
 * seeded {@link Bug} via {@link EnableBug @EnableBug} and runs a <em>correct</em>
 * positive scenario with {@code @Scenario(expect = FAIL)}: a failing step is the
 * success condition, proving the oracle catches the defect instead of rubber-stamping
 * it. See {@code tasks/task-07-fault-detection.md}.
 *
 * <p>This layer asserts only that the scenario fails <em>somewhere</em> (the
 * {@code @TestTemplate} reports one sub-test per step and the expected failure turns
 * green). For a stricter assertion that the failure lands at the exact step the bug
 * manifests at, see {@link FaultDetectionTests}, which drives the standalone executor
 * directly.
 *
 * <p>Gated on the LLM; run with {@code INQUISITOR_LLM_IT=true}.
 */
@Harness(scenarioDir = "classpath:scenarios/explicit/")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RequiresLlm
class FaultDetectionSuiteTest {

    @EnableBug(Bug.DEPOSIT_NOT_PERSISTED)
    @Scenario(expect = FAIL)
    void openAccountAndDeposit() {}

    @EnableBug(Bug.TRANSFER_CREDIT_DROPPED)
    @Scenario(expect = FAIL)
    void transferBetweenAccounts() {}

    @EnableBug(Bug.WRONG_CURRENCY)
    @Scenario(value = "classpath:scenarios/explicit/open-account-and-deposit.md", expect = FAIL)
    void accountOpenedInWrongCurrency() {}
}
