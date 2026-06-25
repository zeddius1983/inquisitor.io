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
import org.springframework.test.context.TestPropertySource;

/**
 * Runs the positive suite against the <em>cucumber</em> style bucket — the same
 * scenarios written in Gherkin {@code Given}/{@code When}/{@code Then} form.
 *
 * <p>OpenAPI discovery is enabled here ({@code inquisitor.harness.openapi.enabled=true}):
 * the Gherkin steps name neither endpoints nor request bodies — they describe intent
 * in {@code Given}/{@code When}/{@code Then} prose — so the model can only succeed by
 * reading the application's OpenAPI description to choose paths, methods, and payload
 * schemas. The {@code explicit} bucket keeps discovery off — its fenced requests
 * already carry full paths and bodies.
 */
@Harness(scenarioDir = "classpath:scenarios/positive/cucumber/")
@TestPropertySource(properties = "inquisitor.harness.openapi.enabled=true")
class CucumberScenarioSuiteTest extends PositiveScenarioSuite {}
