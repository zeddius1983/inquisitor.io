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

package io.inquisitor.harness.evaluation.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.logging.ModelRegistry;
import io.inquisitor.harness.logging.ModelRole;
import io.inquisitor.harness.logging.markdown.MarkdownLogSupport;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.evaluation.EvaluationResponse;

class MarkdownEvaluationLoggerTest {

    private static final List<Step> STEPS = List.of(
            new Step(1, "Step 1 — Reset the database", "Reset it."),
            new Step(2, "Step 2 — Import accounts", "Import them."),
            new Step(3, "Step 3 — Fetch accounts", "Fetch them."),
            new Step(4, "Step 4 — Verify accounts", "Verify them."));
    private static final Scenario SCENARIO = new Scenario(
            "Import accounts from CSV and plain text", "Import accounts.", STEPS, null);

    @Test
    void rendersEvaluationStartAsABreadcrumb() {
        val registry = new ModelRegistry();
        val request = request();

        val events = capture(() ->
                new MarkdownEvaluationLogger(registry).evaluationStarted(request, actorRun()));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getMarkerList())
                    .extracting(Object::toString)
                    .containsExactly(MarkdownLogSupport.MARKER_NAME);
            assertThat(event.getFormattedMessage())
                    .startsWith("\n\n")
                    .endsWith("\n")
                    .contains("###  Import accounts from CSV and plain text  "
                            + "Reset the database  ▰▱▱▱▱ 1/4  EVALUATION ")
                    .doesNotContain("**Scenario:**", "**Step:**");
        });
    }

    @Test
    void rendersJudgeResultWithModelScoreDurationAndWrappedFeedback() {
        val registry = new ModelRegistry();
        resolve(registry, "/models/snapshots/abc/gemma-4-31B-it-qat-UD-Q4_K_XL.gguf");
        val request = request();
        val feedback = "The trace confirms the execution of the TRUNCATE statement and the "
                + "result indicates successful execution with no existing rows to delete, "
                + "which fully supports the actor's PASS verdict.";
        val response = new EvaluationResponse(
                true, 1.0f, feedback, Map.of("category", "GROUNDED"));

        val events = capture(() -> new MarkdownEvaluationLogger(registry)
                .evaluationCompleted(request, actorRun(), response, Duration.ofMillis(9_807)));

        assertThat(events).singleElement().satisfies(event -> {
            val message = event.getFormattedMessage();
            val lines = message.lines().toList();
            val breadcrumb = lines.stream()
                    .filter(line -> line.startsWith("### "))
                    .findFirst()
                    .orElseThrow()
                    .substring("### ".length());
            val feedbackLines = lines.stream()
                    .filter(line -> line.startsWith("> "))
                    .toList();

            assertThat(message).contains(
                    "###  gemma-4-31B-it-qat-UD-Q4\\_K\\_XL  "
                            + "Import accounts from CSV and plain text  Reset the database  "
                            + "▰▱▱▱▱ 1/4  GROUNDED  Score 1.000  ⧖ 9.807 s ",
                    "### Feedback");
            assertThat(message).doesNotContain("/models/", ".gguf", "- **Feedback:**");
            assertThat(feedbackLines).hasSizeGreaterThan(1)
                    .allSatisfy(line -> assertThat(line.length())
                            .isLessThanOrEqualTo(breadcrumb.length()));
            assertThat(String.join(" ", feedbackLines.stream()
                    .map(line -> line.substring(2))
                    .toList())).isEqualTo(feedback);
        });
    }

    @Test
    void toleratesMissingFeedbackFromACustomEvaluator() {
        val response = new EvaluationResponse(
                true, 1.0f, null, Map.of("category", "GROUNDED"));

        val events = capture(() -> new MarkdownEvaluationLogger(new ModelRegistry())
                .evaluationCompleted(request(), actorRun(), response, Duration.ofSeconds(1)));

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage())
                        .contains("GROUNDED", "### Feedback", "\n\n>"));
    }

    private static StepRequest request() {
        return StepRequest.of("conversation", SCENARIO, STEPS.getFirst());
    }

    private static StepRun actorRun() {
        return new StepRun(
                new StepVerdict(Outcome.PASS, "Database reset.", List.of()),
                List.of(), Duration.ofSeconds(2));
    }

    private static void resolve(ModelRegistry registry, String model) {
        val metadata = mock(ChatResponseMetadata.class);
        when(metadata.getModel()).thenReturn(model);
        registry.resolve(ModelRole.JUDGE, metadata);
    }

    private static List<ILoggingEvent> capture(Runnable invocation) {
        val logger = (Logger) LoggerFactory.getLogger(MarkdownEvaluationLogger.class);
        val originalLevel = logger.getLevel();
        val originalAdditive = logger.isAdditive();
        val appender = new ListAppender<ILoggingEvent>();
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        appender.start();
        logger.addAppender(appender);
        try {
            invocation.run();
            val events = List.copyOf(appender.list);
            assertThat(events).allSatisfy(event ->
                    assertThat(event.getLevel()).isEqualTo(Level.INFO));
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
