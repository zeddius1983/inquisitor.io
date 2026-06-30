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

import io.inquisitor.harness.config.InquisitorHarnessProperties;
import io.inquisitor.harness.evaluation.CredibilityEvaluationStepRunner;
import io.inquisitor.harness.evaluation.CredibilityEvaluator;
import io.inquisitor.harness.evaluation.CredibilityRecorder;
import io.inquisitor.harness.executor.LlmStepRunner;
import io.inquisitor.harness.executor.StepRunner;
import lombok.val;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
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
 * Wires credibility evaluation when {@code inquisitor.harness.evaluation.enabled=true}: a
 * separate judge model, the {@link CredibilityEvaluator}, a {@link CredibilityRecorder},
 * and a {@link CredibilityEvaluationStepRunner} that wraps the actor {@link LlmStepRunner}
 * (made {@code @Primary} so the executor uses it). When disabled, none of this loads and
 * the executor uses the plain {@link LlmStepRunner}.
 *
 * <p>The judge is an OpenAI-compatible model configured from
 * {@code inquisitor.harness.evaluation.{model,baseUrl,apiKey}}; {@code baseUrl}/{@code apiKey}
 * fall back to the actor's {@code spring.ai.openai.*} settings. It should be a different
 * model from the actor — a self-judge shares its failure modes.
 */
@AutoConfiguration(after = InquisitorHarnessAutoConfiguration.class)
@ConditionalOnClass({ChatClient.class, Evaluator.class, OpenAiChatModel.class})
@ConditionalOnProperty(prefix = "inquisitor.harness.evaluation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(InquisitorHarnessProperties.class)
public class InquisitorEvaluationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CredibilityRecorder inquisitorCredibilityRecorder() {
        return new CredibilityRecorder();
    }

    @Bean
    @ConditionalOnMissingBean
    Evaluator inquisitorCredibilityEvaluator(InquisitorHarnessProperties properties, Environment environment) {
        val evaluation = properties.evaluation();
        val baseUrl = evaluation.baseUrl() != null
                ? evaluation.baseUrl()
                : environment.getProperty("spring.ai.openai.base-url");
        val apiKey = evaluation.apiKey() != null
                ? evaluation.apiKey()
                : environment.getProperty("spring.ai.openai.api-key");
        Assert.hasText(evaluation.model(),
                "inquisitor.harness.evaluation.model must be set when evaluation is enabled");
        Assert.hasText(baseUrl, "No judge base URL: set inquisitor.harness.evaluation.base-url "
                + "or spring.ai.openai.base-url");
        Assert.hasText(apiKey, "No judge API key: set inquisitor.harness.evaluation.api-key "
                + "or spring.ai.openai.api-key");

        val judgeModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .model(evaluation.model())
                        .temperature(0.0d)
                        .build())
                .build();
        // A bare judge: no harness system prompt, no chat memory, no tools — it rules on
        // the trace it is given, nothing else.
        return new CredibilityEvaluator(ChatClient.builder(judgeModel).build());
    }

    @Bean
    @Primary
    @ConditionalOnBean(LlmStepRunner.class)
    StepRunner inquisitorCredibilityStepRunner(
            LlmStepRunner llmStepRunner, Evaluator evaluator, CredibilityRecorder recorder) {
        return new CredibilityEvaluationStepRunner(llmStepRunner, evaluator, recorder);
    }
}
