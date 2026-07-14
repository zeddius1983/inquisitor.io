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

import io.inquisitor.harness.autoconfigure.InquisitorHarnessAutoConfiguration;
import io.inquisitor.harness.evaluation.EvaluationProperties;
import io.inquisitor.harness.evaluation.EvaluationStepRunner;
import io.inquisitor.harness.evaluation.RecordingToolCallback;
import io.inquisitor.harness.evaluation.StepEvaluationRecorder;
import io.inquisitor.harness.evaluation.StepEvaluator;
import io.inquisitor.harness.executor.LlmStepRunner;
import io.inquisitor.harness.executor.StepRunner;
import lombok.val;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * Wires step evaluation when {@code inquisitor.harness.evaluation.enabled=true}: a
 * {@link BeanPostProcessor} that decorates every harness {@link ToolCallback} to record
 * its calls, a separate judge model, the {@link StepEvaluator}, a
 * {@link StepEvaluationRecorder}, and a {@link EvaluationStepRunner} that wraps the actor
 * {@link LlmStepRunner} (made {@code @Primary} so the executor uses it). When disabled — or
 * when this starter is absent — none of this loads and the harness runs the plain
 * {@link LlmStepRunner} with undecorated tools.
 *
 * <p>The judge is an OpenAI-compatible model configured from
 * {@code inquisitor.harness.evaluation.{model,baseUrl,apiKey}}; {@code baseUrl}/{@code apiKey}
 * fall back to the actor's {@code spring.ai.openai.*} settings. It should be a different
 * model from the actor — a self-judge shares its failure modes.
 */
@AutoConfiguration(after = InquisitorHarnessAutoConfiguration.class)
@ConditionalOnClass({ChatClient.class, Evaluator.class, OpenAiChatModel.class})
@ConditionalOnProperty(prefix = "inquisitor.harness.evaluation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EvaluationProperties.class)
public class InquisitorEvaluationAutoConfiguration {

    /**
     * Decorates every {@link ToolCallback} bean so each call is recorded into the per-step
     * ledger the judge audits against — the built-in HTTP/SQL adapters and any
     * user-supplied callbacks alike. The wrapper is transparent (it records only when the
     * runner has installed a ledger), so the core never has to know about evaluation.
     *
     * <p>{@code static} so the post-processor is registered without forcing early
     * instantiation of this configuration class.
     */
    @Bean
    static BeanPostProcessor inquisitorRecordingToolCallbackDecorator() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof ToolCallback callback && !(callback instanceof RecordingToolCallback)
                        ? new RecordingToolCallback(callback)
                        : bean;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    StepEvaluationRecorder inquisitorStepEvaluationRecorder() {
        return new StepEvaluationRecorder();
    }

    @Bean
    @ConditionalOnMissingBean
    Evaluator inquisitorStepEvaluator(EvaluationProperties properties, Environment environment) {
        val baseUrl = properties.baseUrl() != null
                ? properties.baseUrl()
                : environment.getProperty("spring.ai.openai.base-url");
        val apiKey = properties.apiKey() != null
                ? properties.apiKey()
                : environment.getProperty("spring.ai.openai.api-key");
        Assert.hasText(properties.model(),
                "inquisitor.harness.evaluation.model must be set when evaluation is enabled");
        Assert.hasText(baseUrl, "No judge base URL: set inquisitor.harness.evaluation.base-url "
                + "or spring.ai.openai.base-url");
        Assert.hasText(apiKey, "No judge API key: set inquisitor.harness.evaluation.api-key "
                + "or spring.ai.openai.api-key");

        val judgeModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .model(properties.model())
                        .temperature(0.0d)
                        .build())
                .build();
        // A bare judge: no harness system prompt, no chat memory, no tools — it rules on
        // the trace it is given, nothing else.
        return new StepEvaluator(ChatClient.builder(judgeModel).build());
    }

    @Bean
    @Primary
    @ConditionalOnBean(LlmStepRunner.class)
    StepRunner inquisitorEvaluationStepRunner(
            LlmStepRunner llmStepRunner, Evaluator evaluator, StepEvaluationRecorder recorder) {
        return new EvaluationStepRunner(llmStepRunner, evaluator, recorder);
    }
}
