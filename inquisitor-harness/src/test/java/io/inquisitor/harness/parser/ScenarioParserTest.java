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

package io.inquisitor.harness.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.inquisitor.harness.model.Scenario;
import org.junit.jupiter.api.Test;

class ScenarioParserTest {

    private final ScenarioParser parser = new ScenarioParser();

    @Test
    void singleStepScenarioHasNoStepHeadings() {
        String markdown = """
                # Fetch a non-existent account

                Call GET /accounts/99999.
                Verify the HTTP status is 404 and the response body is a valid RFC 9457 problem detail.
                """;

        Scenario scenario = parser.parse(markdown, "scenarios/account-not-found.md");

        assertThat(scenario.name()).isEqualTo("Fetch a non-existent account");
        assertThat(scenario.description()).isEmpty();
        assertThat(scenario.isMultiStep()).isFalse();
        assertThat(scenario.steps()).hasSize(1);
        assertThat(scenario.source()).isEqualTo("scenarios/account-not-found.md");

        var step = scenario.steps().get(0);
        assertThat(step.index()).isEqualTo(1);
        assertThat(step.title()).isEqualTo("Fetch a non-existent account");
        assertThat(step.instruction())
                .startsWith("Call GET /accounts/99999.")
                .contains("404");
    }

    @Test
    void multiStepScenarioSplitsOnLevelTwoHeadings() {
        String markdown = """
                # Order lifecycle

                This scenario exercises the order API end to end.

                ## Step 1 - Create an order
                Create an order via POST /orders with two line items.
                Remember the returned order id.

                ## Step 2 - Load the order
                Load the order created in step 1 via GET /orders/{id}.
                Verify it has two line items.

                ## Step 3 - Delete the order
                Delete the order via DELETE /orders/{id}.
                Verify GET /orders/{id} now returns 404.
                """;

        Scenario scenario = parser.parse(markdown);

        assertThat(scenario.name()).isEqualTo("Order lifecycle");
        assertThat(scenario.description()).isEqualTo("This scenario exercises the order API end to end.");
        assertThat(scenario.isMultiStep()).isTrue();
        assertThat(scenario.steps()).hasSize(3);

        assertThat(scenario.steps())
                .extracting("index", "title")
                .containsExactly(
                        tuple(1, "Step 1 - Create an order"),
                        tuple(2, "Step 2 - Load the order"),
                        tuple(3, "Step 3 - Delete the order"));

        assertThat(scenario.steps().get(0).instruction())
                .startsWith("Create an order via POST /orders")
                .contains("Remember the returned order id.");
        assertThat(scenario.steps().get(2).instruction()).contains("404");
    }

    @Test
    void deeperHeadingsStayWithinTheirStep() {
        String markdown = """
                # Nested

                ## Step 1 - Complex
                Do setup.

                ### Sub detail
                More detail under step 1.
                """;

        Scenario scenario = parser.parse(markdown);

        assertThat(scenario.steps()).hasSize(1);
        assertThat(scenario.steps().get(0).instruction())
                .contains("Do setup.")
                .contains("### Sub detail")
                .contains("More detail under step 1.");
    }

    @Test
    void deriveNameFromSourceWhenNoTitleHeading() {
        String markdown = """
                ## Step 1 - Do thing
                Do the thing.

                ## Step 2 - Check thing
                Check it.
                """;

        Scenario scenario = parser.parse(markdown, "scenarios/my-scenario.md");

        assertThat(scenario.name()).isEqualTo("my-scenario");
        assertThat(scenario.steps()).hasSize(2);
    }

    @Test
    void fallsBackToDefaultNameWhenNoTitleAndNoSource() {
        Scenario scenario = parser.parse("Just do something and verify it.");

        assertThat(scenario.name()).isEqualTo("scenario");
        assertThat(scenario.steps()).hasSize(1);
        assertThat(scenario.steps().get(0).instruction()).isEqualTo("Just do something and verify it.");
    }
}
