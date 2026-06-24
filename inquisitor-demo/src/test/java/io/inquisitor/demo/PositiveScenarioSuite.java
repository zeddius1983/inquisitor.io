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

import io.inquisitor.harness.junit.Scenario;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The positive scenario suite, run through the ergonomic harness JUnit layer. Each
 * {@link Scenario @Scenario} method is one scenario (its markdown resolved from the
 * method name) and every {@code ## Step} is reported as its own sub-test.
 *
 * <p>The same set of scenarios is authored in two engineering styles — see
 * {@code scenarios/positive/explicit} (structured, prescriptive) and
 * {@code scenarios/positive/cucumber} (Gherkin Given/When/Then). This base holds the
 * scenario methods once; each concrete subclass binds them to one style bucket via
 * {@link io.inquisitor.harness.junit.Harness#scenarioDir()}, so we can compare how a
 * model copes with the two writing styles over identical scenarios.
 *
 * <p>Gated on the local LLM; run with {@code INQUISITOR_LLM_IT=true}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "INQUISITOR_LLM_IT", matches = "true")
abstract class PositiveScenarioSuite {

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

    @Scenario
    void importAccounts() {}
}
