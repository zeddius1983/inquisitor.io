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

import javax.sql.DataSource;

import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.DataSourceRegistry;
import io.inquisitor.harness.tool.HttpRequestTool;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import io.inquisitor.harness.tool.SqlTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
            // Tools and registries do not need a model and are still available.
            assertThat(context).hasSingleBean(HttpRequestTool.class);
            assertThat(context).hasSingleBean(SqlTool.class);
        });
    }
}
