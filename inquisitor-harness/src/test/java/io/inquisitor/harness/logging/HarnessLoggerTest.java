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

package io.inquisitor.harness.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.logging.markdown.MarkdownHttpRequestLogger;
import io.inquisitor.harness.logging.markdown.MarkdownLlmLogger;
import io.inquisitor.harness.logging.markdown.MarkdownLogSupport;
import io.inquisitor.harness.logging.markdown.MarkdownScenarioLogger;
import io.inquisitor.harness.logging.markdown.MarkdownSqlLogger;
import io.inquisitor.harness.logging.plain.PlainHttpRequestLogger;
import io.inquisitor.harness.logging.plain.PlainLlmLogger;
import io.inquisitor.harness.logging.plain.PlainScenarioLogger;
import io.inquisitor.harness.logging.plain.PlainSqlLogger;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.ScenarioResult;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepResult;
import io.inquisitor.harness.model.StepVerdict;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;

class HarnessLoggerTest {

    private static final Scenario SCENARIO = new Scenario(
            "Account lifecycle", "Open and fund Bob's account.",
            List.of(new Step(1, "Step 1 — Open account", "Create Bob in USD.")), null);

    @Test
    void markdownScenarioEventsAreMarkedAndPaddedWithBlankLines() {
        val events = capture(MarkdownScenarioLogger.class, Level.INFO,
                () -> new MarkdownScenarioLogger().scenarioStarted(SCENARIO));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList())
                    .extracting(Object::toString)
                    .containsExactly(MarkdownLogSupport.MARKER_NAME);
            assertThat(event.getFormattedMessage()).startsWith("\n\n").endsWith("\n");
            assertThat(event.getFormattedMessage())
                    .contains(
                            "###  Account lifecycle  ▱▱▱▱▱ 0/1  RUN ",
                            "# Account lifecycle",
                            "Open and fund Bob's account.");
        });
    }

    @Test
    void markdownScenarioCompletionUsesProgressOutcomeAndTotalDurationBreadcrumb() {
        val result = new ScenarioResult(SCENARIO, List.of(new StepResult(
                SCENARIO.steps().getFirst(),
                new StepVerdict(Outcome.PASS, "verified", List.of()),
                Duration.ofMillis(73_289))));

        val events = capture(MarkdownScenarioLogger.class, Level.INFO,
                () -> new MarkdownScenarioLogger().scenarioCompleted(result));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList())
                    .extracting(Object::toString)
                    .containsExactly(MarkdownLogSupport.MARKER_NAME);
            assertThat(event.getFormattedMessage())
                    .isEqualTo("\n\n###  Account lifecycle  ▰▰▰▰▰ 1/1 "
                            + " PASS  ⧖ 1 min 13.289 s \n");
        });
    }

    @Test
    void markdownActorEventsAreMarkedAndPaddedWithBlankLines() {
        val request = StepRequest.of("conversation", SCENARIO, SCENARIO.steps().getFirst());
        val run = new StepRun(
                new StepVerdict(Outcome.PASS, "verified\nwith evidence", List.of()),
                List.of(), Duration.ofMillis(13_289));
        val registry = new ModelRegistry();

        val events = capture(MarkdownLlmLogger.class, Level.INFO, () -> {
            val logger = new MarkdownLlmLogger(registry);
            logger.stepStarted(request);
            resolve(registry, ModelRole.ACTOR,
                    "/models/snapshots/abc/gemma-4-31B-it-qat-UD-Q4_K_XL.GGUF");
            logger.stepCompleted(request, run);
        });

        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getMarkerList())
                    .extracting(Object::toString)
                    .containsExactly(MarkdownLogSupport.MARKER_NAME);
            assertThat(event.getFormattedMessage()).startsWith("\n\n").endsWith("\n");
        });
        assertThat(events.get(0).getFormattedMessage()).contains(
                "###  Account lifecycle  Open account  ▰▰▰▰▰ 1/1  RUN ",
                "## Step 1 — Open account",
                "Create Bob in USD.");
        assertThat(events.get(1).getFormattedMessage())
                .contains(
                        "###  gemma-4-31B-it-qat-UD-Q4_K_XL  Account lifecycle  Open account  ▰▰▰▰▰ 1/1  PASS  ⧖ 13.289 s ",
                        "### Reasoning",
                        "> verified\n> with evidence")
                .doesNotContain("/models/", ".GGUF", "**Duration:**", "- **Reasoning:**");
    }

    @Test
    void markdownActorBreadcrumbShowsRoundedStepProgress() {
        val steps = List.of(
                new Step(1, "Step 1 — Import Alice", "Import Alice."),
                new Step(2, "Step 2 — Import Bob", "Import Bob."),
                new Step(3, "Step 3 — Import Carol", "Import Carol."),
                new Step(4, "Step 4 — Verify accounts", "Verify all accounts."));
        val scenario = new Scenario("Import accounts", "", steps, null);
        val request = StepRequest.of("conversation", scenario, steps.get(1));

        val events = capture(MarkdownLlmLogger.class, Level.INFO,
                () -> new MarkdownLlmLogger(new ModelRegistry()).stepStarted(request));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains(
                        " Import accounts  Import Bob  ▰▰▰▱▱ 2/4  RUN "));
    }

    @Test
    void markdownActorReasoningWrapsToTheCompletionBreadcrumbWidth() {
        val request = StepRequest.of("conversation", SCENARIO, SCENARIO.steps().getFirst());
        val reasoning = "The request returned a 201 Created status. The response body contains "
                + "three accounts: Alice USD, Bob EUR, and Carol GBP, where Carol's currency "
                + "correctly defaulted to the value provided in the X-Default-Currency header.";
        val run = new StepRun(
                new StepVerdict(Outcome.PASS, reasoning, List.of()),
                List.of(), Duration.ofSeconds(2));

        val events = capture(MarkdownLlmLogger.class, Level.INFO,
                () -> new MarkdownLlmLogger(new ModelRegistry()).stepCompleted(request, run));

        assertThat(events).singleElement().satisfies(event -> {
            val lines = event.getFormattedMessage().lines().toList();
            val breadcrumb = lines.stream()
                    .filter(line -> line.startsWith("### "))
                    .findFirst()
                    .orElseThrow()
                    .substring("### ".length());
            val reasoningLines = lines.stream()
                    .filter(line -> line.startsWith("> "))
                    .toList();
            assertThat(reasoningLines).hasSizeGreaterThan(1)
                    .allSatisfy(line -> assertThat(line.length())
                            .isLessThanOrEqualTo(breadcrumb.length()));
            assertThat(String.join(" ", reasoningLines.stream()
                    .map(line -> line.substring(2))
                    .toList())).isEqualTo(reasoning);
        });
    }

    @Test
    void markdownActorCompletionToleratesMissingReasoning() {
        val request = StepRequest.of("conversation", SCENARIO, SCENARIO.steps().getFirst());
        val run = new StepRun(
                new StepVerdict(Outcome.PASS, null, List.of()),
                List.of(), Duration.ofSeconds(1));

        val events = capture(MarkdownLlmLogger.class, Level.INFO,
                () -> new MarkdownLlmLogger(new ModelRegistry()).stepCompleted(request, run));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .contains("### Reasoning", "\n\n>"));
    }

    @Test
    void markdownScenarioAbortPreservesThrowableWhenItsMessageContainsPlaceholders() {
        val cause = new IllegalStateException("response body contained {}");
        val result = new ScenarioResult(SCENARIO, List.of());

        val events = capture(MarkdownScenarioLogger.class, Level.INFO,
                () -> new MarkdownScenarioLogger().scenarioAborted(result, cause));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(IllegalStateException.class.getName());
            assertThat(event.getThrowableProxy().getMessage())
                    .isEqualTo("response body contained {}");
            assertThat(event.getFormattedMessage())
                    .contains("Scenario aborted", "infrastructure failure")
                    .doesNotContain("response body contained");
        });
    }

    @Test
    void markdownHttpRequestShowsBreadcrumbHeadersAndPrettyJsonBody() {
        val headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer secret");
        val request = new HttpRequestLogger.Request(
                "localhost", "POST", "/accounts/import", headers,
                "[{\"id\":1,\"owner\":\"Alice\"},{\"id\":2,\"owner\":\"Bob\"}]");

        val events = capture(MarkdownHttpRequestLogger.class, Level.INFO,
                () -> new MarkdownHttpRequestLogger().requestStarted(request));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList())
                    .extracting(Object::toString)
                    .containsExactly(MarkdownLogSupport.MARKER_NAME);
            assertThat(event.getFormattedMessage())
                    .startsWith("\n\n")
                    .endsWith("\n")
                    .contains(
                            "###  HTTP  localhost  POST  /accounts/import ",
                            "### Headers",
                            "```http",
                            "Content-Type: application/json",
                            "Authorization: [REDACTED]",
                            "### Body",
                            "```json",
                            "\"owner\" : \"Alice\"",
                            "\"owner\" : \"Bob\"")
                    .doesNotContain("Bearer secret");
        });
    }

    @Test
    void markdownHttpResponseAddsStatusAndPrettyPrintsJson() {
        val request = new HttpRequestLogger.Request(
                "localhost", "POST", "/accounts/import", Map.of(), null);
        val response = new HttpRequestLogger.Response(
                201, "application/json",
                "[{\"id\":1,\"owner\":\"Alice\"},{\"id\":2,\"owner\":\"Bob\"}]");

        val events = capture(MarkdownHttpRequestLogger.class, Level.INFO,
                () -> new MarkdownHttpRequestLogger().requestCompleted(request, response));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains(
                        "###  HTTP  localhost  POST  /accounts/import  HTTP 201 ",
                        "### Response",
                        "```json",
                        "\"id\" : 1",
                        "\"id\" : 2"));
    }

    @Test
    void plainHttpEventsDoNotCarryTheMarkdownMarker() {
        val request = new HttpRequestLogger.Request(
                "app", "GET", "/accounts/99999",
                Map.of("Authorization", "Bearer secret"), null);

        val events = capture(PlainHttpRequestLogger.class, Level.DEBUG,
                () -> new PlainHttpRequestLogger().requestStarted(request));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList()).isNullOrEmpty();
            assertThat(event.getFormattedMessage())
                    .isEqualTo("httpRequest <- target=app, method=GET, "
                            + "path=/accounts/99999, "
                            + "headers={Authorization=[REDACTED]}, body=null")
                    .doesNotContain("Bearer secret");
        });
    }

    @Test
    void markdownSqlRequestShowsDatasourceBreadcrumbAndHighlightedStatement() {
        val request = new SqlLogger.Request(
                "app", "SELECT owner, currency FROM account ORDER BY owner");

        val events = capture(MarkdownSqlLogger.class, Level.INFO,
                () -> new MarkdownSqlLogger().statementStarted(request));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList())
                    .extracting(Object::toString)
                    .containsExactly(MarkdownLogSupport.MARKER_NAME);
            assertThat(event.getFormattedMessage())
                    .startsWith("\n\n")
                    .endsWith("\n")
                    .contains(
                            "###  SQL  app  EXECUTE ",
                            "### Statement",
                            "```sql",
                            "SELECT owner, currency FROM account ORDER BY owner");
        });
    }

    @Test
    void markdownSqlResponseShowsSuccessBreadcrumbAndResult() {
        val request = new SqlLogger.Request("app", "SELECT owner FROM account");
        val response = new SqlLogger.Response("1 row(s): [{owner=Alice}]");

        val events = capture(MarkdownSqlLogger.class, Level.INFO,
                () -> new MarkdownSqlLogger().statementCompleted(request, response));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains(
                        "###  SQL  app  SUCCESS ",
                        "### Result",
                        "```text",
                        "1 row(s): [{owner=Alice}]"));
    }

    @Test
    void plainSqlEventsDoNotCarryTheMarkdownMarker() {
        val request = new SqlLogger.Request("app", "TRUNCATE TABLE account");

        val events = capture(PlainSqlLogger.class, Level.DEBUG,
                () -> new PlainSqlLogger().statementStarted(request));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList()).isNullOrEmpty();
            assertThat(event.getFormattedMessage())
                    .isEqualTo("sqlQuery <- datasource=app, sql=TRUNCATE TABLE account");
        });
    }

    @Test
    void plainScenarioStartOmitsTheMarkdownDescription() {
        val events = capture(PlainScenarioLogger.class, Level.INFO,
                () -> new PlainScenarioLogger().scenarioStarted(SCENARIO));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList()).isNullOrEmpty();
            assertThat(event.getFormattedMessage())
                    .isEqualTo("Scenario START: Account lifecycle")
                    .doesNotContain("Open and fund Bob's account.");
        });
    }

    @Test
    void plainStepStartOmitsTheMarkdownInstruction() {
        val request = StepRequest.of("conversation", SCENARIO, SCENARIO.steps().getFirst());

        val events = capture(PlainLlmLogger.class, Level.DEBUG,
                () -> new PlainLlmLogger().stepStarted(request));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList()).isNullOrEmpty();
            assertThat(event.getFormattedMessage())
                    .isEqualTo("[Account lifecycle] step 1/1 - RUN: Step 1 — Open account")
                    .doesNotContain("Create Bob in USD.");
        });
    }

    private static void resolve(ModelRegistry registry, ModelRole role, String model) {
        val metadata = mock(ChatResponseMetadata.class);
        when(metadata.getModel()).thenReturn(model);
        registry.resolve(role, metadata);
    }

    private static List<ILoggingEvent> capture(
            Class<?> loggerType,
            Level level,
            Runnable invocation) {
        val logger = (Logger) LoggerFactory.getLogger(loggerType);
        val originalLevel = logger.getLevel();
        val originalAdditive = logger.isAdditive();
        val appender = new ListAppender<ILoggingEvent>();
        logger.setLevel(level);
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
        try {
            invocation.run();
            val events = List.copyOf(appender.list);
            if (loggerType.getSimpleName().startsWith("Markdown")) {
                assertThat(events).allSatisfy(event ->
                        assertThat(event.getLevel()).isEqualTo(Level.INFO));
            }
            return events;
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }
    }
}
