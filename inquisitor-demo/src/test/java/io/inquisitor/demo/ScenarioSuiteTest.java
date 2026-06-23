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

import io.inquisitor.harness.junit.Harness;
import io.inquisitor.harness.junit.Scenario;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs the markdown scenarios through the ergonomic harness JUnit layer: each
 * {@link Scenario @Scenario} method is one scenario (its markdown resolved from the
 * method name under {@code classpath:scenarios/}), and every {@code ## Step} is
 * reported as its own sub-test. The app's HTTP target is registered automatically by
 * {@link Harness @Harness} — no per-scenario boilerplate.
 *
 * <p>This is the consumer-facing counterpart to {@link ScenarioTests}, which drives
 * the same scenarios via the standalone harness contract. Both are gated on the
 * local LLM; run with {@code INQUISITOR_LLM_IT=true}.
 */
@Harness
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INQUISITOR_LLM_IT", matches = "true")
class ScenarioSuiteTest {

    @Scenario
    void accountNotFound() {}

    @Scenario
    void openAccountAndDeposit() {}

    @Scenario
    void transferBetweenAccounts() {}

    @Scenario
    void overdraftRejected() {}

    @Scenario
    void transactionHistory() {}

    @Scenario
    void databaseState() {}
}
