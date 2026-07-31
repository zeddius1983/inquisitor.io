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
import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
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
                    .contains("# Account lifecycle", "Open and fund Bob's account.");
        });
    }

    @Test
    void markdownActorEventsAreMarkedAndPaddedWithBlankLines() {
        val request = StepRequest.of("conversation", SCENARIO, SCENARIO.steps().getFirst());
        val run = new StepRun(
                new StepVerdict(Outcome.PASS, "verified\nwith evidence", List.of()),
                List.of(), Duration.ofMillis(13_289));
        val registry = new ModelRegistry();
        registry.configure(actorSnapshot().configuration());

        val events = capture(MarkdownLlmLogger.class, Level.DEBUG, () -> {
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
                "###  Account lifecycle  Open account  ▰▰▰▰▰ 1/1  Running ",
                "## Step 1 — Open account",
                "Create Bob in USD.");
        assertThat(events.get(1).getFormattedMessage())
                .contains(
                        "###  gemma-4-31B-it-qat-UD-Q4_K_XL  Account lifecycle  Open account  ▰▰▰▰▰ 1/1  PASS  ⧖ 13.289 s ",
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

        val events = capture(MarkdownLlmLogger.class, Level.DEBUG,
                () -> new MarkdownLlmLogger(new ModelRegistry()).stepStarted(request));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains(
                        " Import accounts  Import Bob  ▰▰▰▱▱ 2/4  Running "));
    }

    @Test
    void plainEventsDoNotCarryTheMarkdownMarker() {
        val events = capture(PlainScenarioLogger.class, Level.INFO,
                () -> new PlainScenarioLogger().scenarioStarted(SCENARIO));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList()).isNullOrEmpty();
            assertThat(event.getFormattedMessage())
                    .startsWith("Scenario START: Account lifecycle")
                    .contains("Open and fund Bob's account.");
        });
    }

    private static ModelSnapshot actorSnapshot() {
        return new ModelSnapshot(new ModelConfiguration(
                ModelRole.ACTOR,
                Optional.of("actor-model"), Optional.of("https://example.test/v1"),
                Optional.of(0.2), Optional.of(0.9), Optional.of(512),
                Optional.empty(), Optional.of("medium")), Optional.empty());
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
            return List.copyOf(appender.list);
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(originalLevel);
            logger.setAdditive(originalAdditive);
        }
    }
}
