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
import io.inquisitor.harness.junit.RequiresLlm;
import io.inquisitor.harness.junit.Scenario;
import io.inquisitor.harness.openapi.EnableOpenApiDiscovery;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Exercises OpenAPI discovery over the full positive suite: the {@code intent}
 * scenarios mirror the {@code explicit} and {@code cucumber} buckets but name no
 * endpoints or payloads, so the model can only succeed by reading the application's
 * OpenAPI spec. (SQL-tool steps keep their SQL — the database schema is not part of
 * the spec.)
 *
 * <p>OpenAPI discovery is enabled only for this class via {@link EnableOpenApiDiscovery};
 * the demo serves its spec at {@code /v3/api-docs.yaml}, which the
 * {@code HttpOpenApiSpecProvider} live-fetches from the running app. Gated on the local
 * LLM; run with {@code INQUISITOR_LLM_IT=true}.
 */
@Harness(scenarioDir = "classpath:scenarios/positive/intent/")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableOpenApiDiscovery
@RequiresLlm
class IntentScenarioSuiteTest {

    @Scenario
    void accountNotFound() {}

    @Scenario
    void openAccountAndDeposit() {}

    @Scenario
    void openAccountsBulk() {}

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
