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

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import io.inquisitor.harness.config.InquisitorHarnessProperties;
import io.inquisitor.harness.executor.ChatClientStepEvaluator;
import io.inquisitor.harness.executor.HarnessSystemPrompt;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepEvaluator;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.DataSourceRegistry;
import io.inquisitor.harness.tool.HttpRequestTool;
import io.inquisitor.harness.tool.HttpTarget;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import io.inquisitor.harness.tool.SqlTool;
import lombok.val;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Wires the harness: the tool registries, the built-in HTTP/SQL tools, the
 * {@link ChatClient} (system prompt + chat memory + every available tool), and the
 * {@link ScenarioExecutor} consumers run scenarios with.
 *
 * <p>The application's own {@link DataSource} is registered automatically (as
 * {@code "app"}); extra targets and datasources come from
 * {@link InquisitorHarnessProperties}. The application's HTTP target depends on the
 * (random) server port and so is registered by the JUnit layer once the server is up,
 * not here.
 *
 * <p>Tools offered to the model are aggregated from three sources: the built-in
 * {@link HttpRequestTool}/{@link SqlTool}, any user-supplied {@link ToolCallback}
 * beans, and any {@link ToolCallbackProvider} beans (e.g. MCP).
 */
@AutoConfiguration
@ConditionalOnClass(ChatClient.class)
@EnableConfigurationProperties(InquisitorHarnessProperties.class)
public class InquisitorHarnessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ChatMemory inquisitorChatMemory() {
        return MessageWindowChatMemory.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    HttpTargetRegistry inquisitorHttpTargetRegistry(InquisitorHarnessProperties properties) {
        val registry = new HttpTargetRegistry();
        properties.targets().forEach((name, target) ->
                registry.register(name, new HttpTarget(target.baseUrl(), target.defaultHeaders())));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    DataSourceRegistry inquisitorDataSourceRegistry(
            InquisitorHarnessProperties properties, ObjectProvider<DataSource> applicationDataSource) {

        val registry = new DataSourceRegistry();
        val appDataSource = applicationDataSource.getIfUnique();
        if (appDataSource != null) {
            registry.register("app", appDataSource);
        }
        properties.datasources().forEach((name, ds) -> registry.register(name, toDataSource(ds)));
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    HttpRequestTool inquisitorHttpRequestTool(HttpTargetRegistry registry) {
        return new HttpRequestTool(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    SqlTool inquisitorSqlTool(DataSourceRegistry registry) {
        return new SqlTool(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    ScenarioParser inquisitorScenarioParser() {
        return new ScenarioParser();
    }

    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean
    ChatClient inquisitorChatClient(
            ChatModel chatModel,
            ChatMemory chatMemory,
            HttpRequestTool httpRequestTool,
            SqlTool sqlTool,
            ObjectProvider<ToolCallback> toolCallbacks,
            ObjectProvider<ToolCallbackProvider> toolCallbackProviders) {

        // Spring AI's unified defaultTools(Object...) accepts tool objects, ToolCallbacks,
        // and ToolCallbackProviders alike, so built-ins and user-supplied tools go together.
        val tools = new ArrayList<>(List.of(httpRequestTool, sqlTool));
        toolCallbacks.orderedStream().forEach(tools::add);
        toolCallbackProviders.orderedStream().forEach(tools::add);

        return ChatClient.builder(chatModel)
                .defaultSystem(HarnessSystemPrompt.TEXT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(tools.toArray())
                .build();
    }

    @Bean
    @ConditionalOnBean(ChatClient.class)
    @ConditionalOnMissingBean
    StepEvaluator inquisitorStepEvaluator(ChatClient chatClient) {
        return new ChatClientStepEvaluator(chatClient);
    }

    @Bean
    @ConditionalOnBean(StepEvaluator.class)
    @ConditionalOnMissingBean
    ScenarioExecutor inquisitorScenarioExecutor(StepEvaluator stepEvaluator) {
        return new ScenarioExecutor(stepEvaluator);
    }

    private static DataSource toDataSource(InquisitorHarnessProperties.Datasource properties) {
        val dataSource = new DriverManagerDataSource(
                properties.url(), properties.username(), properties.password());
        if (properties.driverClassName() != null) {
            dataSource.setDriverClassName(properties.driverClassName());
        }
        return dataSource;
    }
}
