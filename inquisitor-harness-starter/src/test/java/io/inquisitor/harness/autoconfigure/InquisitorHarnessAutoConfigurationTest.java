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
import static org.mockito.Mockito.when;

import javax.sql.DataSource;

import io.inquisitor.harness.config.HarnessLoggingFormat;
import io.inquisitor.harness.config.InquisitorHarnessProperties;
import io.inquisitor.harness.executor.LlmStepRunnerCallback;
import io.inquisitor.harness.executor.ScenarioExecutionCallback;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepRunner;
import io.inquisitor.harness.logging.MarkdownLlmLogger;
import io.inquisitor.harness.logging.MarkdownScenarioLogger;
import io.inquisitor.harness.logging.ModelRegistry;
import io.inquisitor.harness.logging.ModelRole;
import io.inquisitor.harness.logging.ModelSnapshot;
import io.inquisitor.harness.logging.PlainLlmLogger;
import io.inquisitor.harness.logging.PlainScenarioLogger;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.DataSourceRegistry;
import io.inquisitor.harness.tool.HttpRequestTool;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import io.inquisitor.harness.tool.SqlTool;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
        });

        runner.withPropertyValues("inquisitor.harness.logging.format=markdown").run(context -> {
            assertThat(context.getBean(InquisitorHarnessProperties.class).logging().format())
                    .isEqualTo(HarnessLoggingFormat.MARKDOWN);
            assertThat(context.getBean(LlmStepRunnerCallback.class))
                    .isInstanceOf(MarkdownLlmLogger.class);
            assertThat(context.getBean(ScenarioExecutionCallback.class))
                    .isInstanceOf(MarkdownScenarioLogger.class);
        });
    }

    @Test
    void allowsSemanticLoggersToBeReplaced() {
        val llmCallback = mock(LlmStepRunnerCallback.class);
        val scenarioCallback = mock(ScenarioExecutionCallback.class);

        runner.withBean(LlmStepRunnerCallback.class, () -> llmCallback)
                .withBean(ScenarioExecutionCallback.class, () -> scenarioCallback)
                .run(context -> {
                    assertThat(context.getBean(LlmStepRunnerCallback.class)).isSameAs(llmCallback);
                    assertThat(context.getBean(ScenarioExecutionCallback.class))
                            .isSameAs(scenarioCallback);
                });
    }

    @Test
    void genericOptionsAreRegisteredForTheActor() {
        val options = ChatOptions.builder()
                .model("generic-model")
                .temperature(0.25)
                .topP(0.8)
                .maxTokens(512)
                .build();
        val chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(options);

        runner.withBean(ChatModel.class, () -> chatModel)
                .withPropertyValues(
                        "spring.ai.openai.base-url=https://common.example/v1")
                .run(context -> {
                    val configuration = actor(context.getBean(ModelRegistry.class)).configuration();
                    assertThat(configuration.configuredModel()).contains("generic-model");
                    assertThat(configuration.baseUrl()).contains("https://common.example/v1");
                    assertThat(configuration.temperature()).contains(0.25);
                    assertThat(configuration.topP()).contains(0.8);
                    assertThat(configuration.maxTokens()).contains(512);
                    assertThat(configuration.maxCompletionTokens()).isEmpty();
                    assertThat(configuration.reasoningEffort()).isEmpty();
                });
    }

    @Test
    void openAiOptionsRegisterReasoningAndMaxCompletionTokens() {
        val options = OpenAiChatOptions.builder()
                .baseUrl("https://programmatic.example/v1")
                .model("openai-model")
                .maxCompletionTokens(2048)
                .reasoningEffort("high")
                .build();
        val chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(options);

        runner.withBean(ChatModel.class, () -> chatModel)
                .withPropertyValues(
                        "spring.ai.openai.chat.base-url=https://chat.example/v1",
                        "spring.ai.openai.base-url=https://common.example/v1")
                .run(context -> {
                    val configuration = actor(context.getBean(ModelRegistry.class)).configuration();
                    assertThat(configuration.baseUrl()).contains("https://programmatic.example/v1");
                    assertThat(configuration.maxCompletionTokens()).contains(2048);
                    assertThat(configuration.reasoningEffort()).contains("high");
                });
    }

    @Test
    void chatBaseUrlPropertyTakesPrecedenceOverTheCommonProperty() {
        val chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());

        runner.withBean(ChatModel.class, () -> chatModel)
                .withPropertyValues(
                        "spring.ai.openai.chat.base-url=https://chat.example/v1",
                        "spring.ai.openai.base-url=https://common.example/v1")
                .run(context -> assertThat(actor(context.getBean(ModelRegistry.class))
                        .configuration().baseUrl()).contains("https://chat.example/v1"));
    }

    @Test
    void configurationBannerDoesNotExposeUrlCredentialsOrQueryValues() {
        val options = OpenAiChatOptions.builder()
                .baseUrl("https://user:secret@example.test/v1?token=sensitive#fragment")
                .model("openai-model")
                .build();
        val chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(options);

        runner.withBean(ChatModel.class, () -> chatModel)
                .run(context -> assertThat(actor(context.getBean(ModelRegistry.class))
                        .configuration().baseUrl()).contains("https://example.test/v1"));
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

    private static ModelSnapshot actor(ModelRegistry registry) {
        return registry.snapshots().stream()
                .filter(snapshot -> snapshot.configuration().role() == ModelRole.ACTOR)
                .findFirst()
                .orElseThrow();
    }
}
