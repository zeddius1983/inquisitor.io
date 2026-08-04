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

package io.inquisitor.harness.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import javax.sql.DataSource;

import io.inquisitor.harness.config.HarnessLoggingFormat;
import io.inquisitor.harness.config.InquisitorHarnessProperties;
import io.inquisitor.harness.executor.LlmStepRunner;
import io.inquisitor.harness.executor.LlmStepRunnerCallback;
import io.inquisitor.harness.executor.ScenarioExecutionCallback;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRunner;
import io.inquisitor.harness.logging.HttpRequestLogger;
import io.inquisitor.harness.logging.LlmLoggerCallback;
import io.inquisitor.harness.logging.ModelRegistry;
import io.inquisitor.harness.logging.ModelRole;
import io.inquisitor.harness.logging.SqlLogger;
import io.inquisitor.harness.logging.markdown.MarkdownHttpRequestLogger;
import io.inquisitor.harness.logging.markdown.MarkdownLlmLogger;
import io.inquisitor.harness.logging.markdown.MarkdownScenarioLogger;
import io.inquisitor.harness.logging.markdown.MarkdownSqlLogger;
import io.inquisitor.harness.logging.plain.PlainHttpRequestLogger;
import io.inquisitor.harness.logging.plain.PlainLlmLogger;
import io.inquisitor.harness.logging.plain.PlainScenarioLogger;
import io.inquisitor.harness.logging.plain.PlainSqlLogger;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.DataSourceRegistry;
import io.inquisitor.harness.tool.HttpRequestTool;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import io.inquisitor.harness.tool.SqlTool;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class InquisitorHarnessAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InquisitorHarnessAutoConfiguration.class));

    @Test
    void registersHarnessBeansWhenAChatModelIsPresent() {
        runner.withBean(ChatModel.class, () -> mock(ChatModel.class)).run(context -> {
            assertThat(context).hasSingleBean(HttpTargetRegistry.class);
            assertThat(context).hasSingleBean(DataSourceRegistry.class);
            assertThat(context).hasSingleBean(HttpRequestTool.class);
            assertThat(context).hasSingleBean(SqlTool.class);
            assertThat(context).hasSingleBean(ScenarioParser.class);
            assertThat(context).hasSingleBean(ChatClient.class);
            assertThat(context).hasSingleBean(ScenarioExecutor.class);
            assertThat(context).hasSingleBean(ModelRegistry.class);
            assertThat(context).hasSingleBean(HttpRequestLogger.class);
            assertThat(context).hasSingleBean(SqlLogger.class);
            assertThat(context.getBean(ModelRegistry.class).actualModel(ModelRole.ACTOR)).isEmpty();
            assertThat(context).hasSingleBean(LlmStepRunnerCallback.class);
            assertThat(context).hasSingleBean(ScenarioExecutionCallback.class);
        });
    }

    @Test
    void loggingDefaultsToPlainAndCanBeConfiguredAsMarkdown() {
        runner.run(context -> {
            assertThat(context.getBean(InquisitorHarnessProperties.class).logging().format())
                    .isEqualTo(HarnessLoggingFormat.PLAIN);
            assertThat(context.getBean(LlmStepRunnerCallback.class))
                    .isInstanceOf(PlainLlmLogger.class);
            assertThat(context.getBean(ScenarioExecutionCallback.class))
                    .isInstanceOf(PlainScenarioLogger.class);
            assertThat(context.getBean(HttpRequestLogger.class))
                    .isInstanceOf(PlainHttpRequestLogger.class);
            assertThat(context.getBean(SqlLogger.class))
                    .isInstanceOf(PlainSqlLogger.class);
        });

        runner.withPropertyValues("inquisitor.harness.logging.format=markdown").run(context -> {
            assertThat(context.getBean(InquisitorHarnessProperties.class).logging().format())
                    .isEqualTo(HarnessLoggingFormat.MARKDOWN);
            assertThat(context.getBean(LlmStepRunnerCallback.class))
                    .isInstanceOf(MarkdownLlmLogger.class);
            assertThat(context.getBean(ScenarioExecutionCallback.class))
                    .isInstanceOf(MarkdownScenarioLogger.class);
            assertThat(context.getBean(HttpRequestLogger.class))
                    .isInstanceOf(MarkdownHttpRequestLogger.class);
            assertThat(context.getBean(SqlLogger.class))
                    .isInstanceOf(MarkdownSqlLogger.class);
        });
    }

    @Test
    void allowsSemanticLoggersToBeReplaced() {
        val llmCallback = mock(LlmLoggerCallback.class);
        val scenarioCallback = mock(ScenarioExecutionCallback.class);
        val httpLogger = mock(HttpRequestLogger.class);
        val sqlLogger = mock(SqlLogger.class);

        runner.withBean(LlmLoggerCallback.class, () -> llmCallback)
                .withBean(ScenarioExecutionCallback.class, () -> scenarioCallback)
                .withBean(HttpRequestLogger.class, () -> httpLogger)
                .withBean(SqlLogger.class, () -> sqlLogger)
                .run(context -> {
                    assertThat(context.getBean(LlmLoggerCallback.class)).isSameAs(llmCallback);
                    assertThat(context.getBean(ScenarioExecutionCallback.class))
                            .isSameAs(scenarioCallback);
                    assertThat(context.getBean(HttpRequestLogger.class)).isSameAs(httpLogger);
                    assertThat(context.getBean(SqlLogger.class)).isSameAs(sqlLogger);
                });
    }

    @Test
    void composesContributedActorCallbackWithSelectedLogger() {
        val observer = mock(LlmStepRunnerCallback.class);

        runner.withBean(ChatModel.class, () -> mock(ChatModel.class))
                .withBean("actorObserver", LlmStepRunnerCallback.class, () -> observer)
                .run(context -> {
                    assertThat(context.getBeansOfType(LlmStepRunnerCallback.class)).hasSize(2);
                    val llmStepRunner = context.getBean(LlmStepRunner.class);
                    val callback = (LlmStepRunnerCallback) ReflectionTestUtils
                            .getField(llmStepRunner, "callback");
                    val scenario = new Scenario("Scenario", "", List.of(
                            new Step(1, "Step", "Run it")), null);
                    val request = StepRequest.of(
                            "conversation", scenario, scenario.steps().getFirst());

                    callback.stepStarted(request);

                    verify(observer).stepStarted(request);
                });
    }

    @Test
    void composesMultipleContributedScenarioCallbacks() {
        val first = mock(ScenarioExecutionCallback.class);
        val second = mock(ScenarioExecutionCallback.class);
        val stepRunner = mock(StepRunner.class);

        runner.withBean(StepRunner.class, () -> stepRunner)
                .withBean("firstScenarioObserver", ScenarioExecutionCallback.class, () -> first)
                .withBean("secondScenarioObserver", ScenarioExecutionCallback.class, () -> second)
                .run(context -> {
                    assertThat(context.getBeansOfType(ScenarioExecutionCallback.class)).hasSize(2);
                    val executor = context.getBean(ScenarioExecutor.class);
                    val callback = (ScenarioExecutionCallback) ReflectionTestUtils
                            .getField(executor, "callback");
                    val scenario = new Scenario("Scenario", "", List.of(
                            new Step(1, "Step", "Run it")), null);

                    callback.scenarioStarted(scenario);

                    verify(first).scenarioStarted(scenario);
                    verify(second).scenarioStarted(scenario);
                });
    }

    @Test
    void bindsExtraTargetsAndDatasourcesFromProperties() {
        runner.withBean(ChatModel.class, () -> mock(ChatModel.class))
                .withPropertyValues(
                        "inquisitor.harness.targets.inventory-mock.base-url=http://localhost:9090",
                        "inquisitor.harness.datasources.reporting.url=jdbc:postgresql://db:5432/rep",
                        "inquisitor.harness.datasources.reporting.username=u",
                        "inquisitor.harness.datasources.reporting.password=p")
                .run(context -> {
                    assertThat(context.getBean(HttpTargetRegistry.class).names()).contains("inventory-mock");
                    assertThat(context.getBean(DataSourceRegistry.class).names()).contains("reporting");
                });
    }

    @Test
    void registersTheApplicationDataSourceAsApp() {
        runner.withBean(ChatModel.class, () -> mock(ChatModel.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .run(context ->
                        assertThat(context.getBean(DataSourceRegistry.class).names()).contains("app"));
    }

    @Test
    void aggregatesUserSuppliedToolCallbacks() {
        runner.withBean(ChatModel.class, () -> mock(ChatModel.class))
                .withBean("customTool", ToolCallback.class, () -> mock(ToolCallback.class))
                .run(context -> assertThat(context).hasSingleBean(ChatClient.class));
    }

    @Test
    void skipsModelDependentBeansWithoutAChatModel() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(ChatClient.class);
            assertThat(context).doesNotHaveBean(ScenarioExecutor.class);
            assertThat(context).hasSingleBean(LlmStepRunnerCallback.class);
            assertThat(context).hasSingleBean(ScenarioExecutionCallback.class);
            assertThat(context).hasSingleBean(ModelRegistry.class);
            // Tools and registries do not need a model and are still available.
            assertThat(context).hasSingleBean(HttpRequestTool.class);
            assertThat(context).hasSingleBean(SqlTool.class);
        });
    }

    @Test
    void createsAnExecutorForAUserStepRunnerWithoutAChatModel() {
        runner.withBean(StepRunner.class, () -> mock(StepRunner.class)).run(context -> {
            assertThat(context).doesNotHaveBean(ChatClient.class);
            assertThat(context).hasSingleBean(LlmStepRunnerCallback.class);
            assertThat(context).hasSingleBean(ScenarioExecutionCallback.class);
            assertThat(context).hasSingleBean(ScenarioExecutor.class);
        });
    }
}
