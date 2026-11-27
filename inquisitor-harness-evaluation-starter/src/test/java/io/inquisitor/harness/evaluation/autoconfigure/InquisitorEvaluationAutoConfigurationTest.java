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

package io.inquisitor.harness.evaluation.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import io.inquisitor.harness.autoconfigure.InquisitorHarnessAutoConfiguration;
import io.inquisitor.harness.evaluation.EvaluationStepRunner;
import io.inquisitor.harness.evaluation.EvaluationStepRunnerCallback;
import io.inquisitor.harness.evaluation.StepEvaluationRecorder;
import io.inquisitor.harness.evaluation.StepEvaluator;
import io.inquisitor.harness.evaluation.StepReviewer;
import io.inquisitor.harness.evaluation.SupervisedStepRunner;
import io.inquisitor.harness.evaluation.logging.EvaluationLoggerCallback;
import io.inquisitor.harness.evaluation.logging.MarkdownEvaluationLogger;
import io.inquisitor.harness.evaluation.logging.PlainEvaluationLogger;
import io.inquisitor.harness.executor.LlmStepRunner;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.executor.StepRunner;
import io.inquisitor.harness.logging.ModelRegistry;
import io.inquisitor.harness.logging.ModelRole;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class InquisitorEvaluationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    InquisitorHarnessAutoConfiguration.class, InquisitorEvaluationAutoConfiguration.class))
            .withBean(ChatModel.class, () -> mock(ChatModel.class));

    @Test
    void refinementUsesSupervisedRunnerAndBindsCorrectionBudget() {
        runner.withPropertyValues(
                        "inquisitor.harness.evaluation.model=judge",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test",
                        "inquisitor.harness.evaluation.refinement.enabled=true",
                        "inquisitor.harness.evaluation.refinement.max-corrections=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(StepRunner.class))
                            .isInstanceOf(SupervisedStepRunner.class);
                    assertThat(context).hasSingleBean(StepReviewer.class);
                    assertThat(context).doesNotHaveBean(Evaluator.class);
                    assertThat(context).doesNotHaveBean(EvaluationStepRunner.class);
                    assertThat(context.getBean(io.inquisitor.harness.evaluation.EvaluationProperties.class)
                            .refinement().maxCorrections()).isEqualTo(1);
                });
    }

    @Test
    void rejectsNegativeCorrectionBudget() {
        runner.withPropertyValues(
                        "inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.refinement.max-corrections=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refinementDoesNotExecuteObservationOnlyEvaluationAndDefaultsToTwoCorrections() {
        runner.withPropertyValues("inquisitor.harness.evaluation.refinement.enabled=true",
                        "inquisitor.harness.evaluation.model=judge",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(StepRunner.class)).isInstanceOf(SupervisedStepRunner.class);
                    assertThat(context).doesNotHaveBean(Evaluator.class);
                    assertThat(context).doesNotHaveBean(EvaluationStepRunner.class);
                    assertThat(context.getBean(io.inquisitor.harness.evaluation.EvaluationProperties.class)
                            .refinement().maxCorrections()).isEqualTo(2);
                });
    }

    @Test
    void refinementTakesPrecedenceWhenBothModesAreEnabled() {
        runner.withPropertyValues("inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.refinement.enabled=true",
                        "inquisitor.harness.evaluation.model=judge",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(StepRunner.class)).isInstanceOf(SupervisedStepRunner.class);
                    assertThat(context).doesNotHaveBean(Evaluator.class);
                    assertThat(context).doesNotHaveBean(EvaluationStepRunner.class);
                });
    }

    @Test
    void decoratesProviderToolsAndRecordsExceptions() {
        val tool = mock(org.springframework.ai.tool.ToolCallback.class);
        org.mockito.Mockito.when(tool.getToolDefinition()).thenReturn(
                org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("providerTool").description("provider tool").inputSchema("{}").build());
        org.mockito.Mockito.when(tool.call(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("write failed"));
        runner.withBean(org.springframework.ai.tool.ToolCallbackProvider.class,
                        () -> () -> new org.springframework.ai.tool.ToolCallback[] {tool})
                .withPropertyValues("inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.model=judge",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test")
                .run(context -> {
                    val callback = context.getBean(org.springframework.ai.tool.ToolCallbackProvider.class)
                            .getToolCallbacks()[0];
                    val ledger = new java.util.ArrayList<io.inquisitor.harness.model.ToolCallRecord>();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> callback.call("{}",
                            new org.springframework.ai.chat.model.ToolContext(java.util.Map.of(
                                    io.inquisitor.harness.tool.TraceKeys.LEDGER, ledger))))
                            .isInstanceOf(IllegalStateException.class);
                    assertThat(ledger).hasSize(1);
                    assertThat(ledger.getFirst().failed()).isTrue();
                });
    }

    @Test
    void evaluationIsOffByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(StepEvaluationRecorder.class);
            assertThat(context).doesNotHaveBean(Evaluator.class);
            assertThat(context.getBean(StepRunner.class)).isInstanceOf(LlmStepRunner.class);
            assertThat(context).hasSingleBean(ScenarioExecutor.class);
        });
    }

    @Test
    void wrapsTheRunnerWithEvaluationWhenEnabled() {
        runner.withPropertyValues(
                        "inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.model=judge-model",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(StepEvaluationRecorder.class);
                    assertThat(context).hasSingleBean(Evaluator.class);
                    assertThat(context).doesNotHaveBean(StepReviewer.class);
                    assertThat(context).doesNotHaveBean(SupervisedStepRunner.class);
                    assertThat(context).hasSingleBean(EvaluationLoggerCallback.class);
                    assertThat(context.getBean(EvaluationLoggerCallback.class))
                            .isInstanceOf(PlainEvaluationLogger.class);
                    assertThat(context.getBeansOfType(EvaluationStepRunnerCallback.class))
                            .hasSize(2);
                    // The @Primary wrapper is what the executor resolves.
                    assertThat(context.getBean(StepRunner.class)).isInstanceOf(EvaluationStepRunner.class);
                    assertThat(context).hasSingleBean(ScenarioExecutor.class);
                    val evaluator = (StepEvaluator) context.getBean(Evaluator.class);
                    val judge = ReflectionTestUtils.getField(evaluator, "judge");
                    val defaultRequest = ReflectionTestUtils.getField(judge, "defaultChatClientRequest");
                    val advisors = (List<?>) ReflectionTestUtils.getField(defaultRequest, "advisors");
                    assertThat(advisors).anySatisfy(advisor ->
                            assertThat(advisor).isInstanceOf(SimpleLoggerAdvisor.class));
                    val models = context.getBean(ModelRegistry.class);
                    assertThat(models.actualModel(ModelRole.ACTOR)).isEmpty();
                    assertThat(models.actualModel(ModelRole.JUDGE)).isEmpty();
                });
    }

    @Test
    void usesMarkdownEvaluationLoggerWhenConfigured() {
        runner.withPropertyValues(
                        "inquisitor.harness.logging.format=markdown",
                        "inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.model=judge-model",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test-key")
                .run(context -> assertThat(context.getBean(EvaluationLoggerCallback.class))
                        .isInstanceOf(MarkdownEvaluationLogger.class));
    }

    @Test
    void backsOffForACustomEvaluationLoggerCallback() {
        val callback = mock(EvaluationLoggerCallback.class);

        runner.withBean(EvaluationLoggerCallback.class, () -> callback)
                .withPropertyValues(
                        "inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.model=judge-model",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test-key")
                .run(context -> assertThat(context.getBean(EvaluationLoggerCallback.class))
                        .isSameAs(callback));
    }

    @Test
    void composesContributedEvaluationCallbackBetweenRecorderAndLogger() {
        val observer = mock(EvaluationStepRunnerCallback.class);

        runner.withBean("evaluationObserver", EvaluationStepRunnerCallback.class, () -> observer)
                .withPropertyValues(
                        "inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.model=judge-model",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test-key")
                .run(context -> {
                    assertThat(context.getBeansOfType(EvaluationStepRunnerCallback.class))
                            .hasSize(3);
                    val stepRunner = context.getBean(EvaluationStepRunner.class);
                    val callback = (EvaluationStepRunnerCallback) ReflectionTestUtils
                            .getField(stepRunner, "callback");
                    val scenario = new Scenario("Scenario", "", List.of(
                            new Step(1, "Step", "Run it")), null);
                    val request = StepRequest.of(
                            "conversation", scenario, scenario.steps().getFirst());
                    val actorRun = new StepRun(
                            new StepVerdict(Outcome.PASS, "done", List.of()),
                            List.of(), Duration.ZERO);

                    callback.evaluationStarted(request, actorRun);

                    verify(observer).evaluationStarted(request, actorRun);
                });
    }

    @Test
    void failsFastWhenEnabledWithoutAJudgeModel() {
        runner.withPropertyValues("inquisitor.harness.evaluation.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
