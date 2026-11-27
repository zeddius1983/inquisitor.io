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

import java.util.Arrays;

import io.inquisitor.harness.autoconfigure.InquisitorHarnessAutoConfiguration;
import io.inquisitor.harness.config.InquisitorHarnessProperties;
import io.inquisitor.harness.evaluation.EvaluationProperties;
import io.inquisitor.harness.evaluation.EvaluationStepRunner;
import io.inquisitor.harness.evaluation.EvaluationStepRunnerCallback;
import io.inquisitor.harness.evaluation.LlmStepReviewer;
import io.inquisitor.harness.evaluation.RecordingToolCallback;
import io.inquisitor.harness.evaluation.StepEvaluationRecorder;
import io.inquisitor.harness.evaluation.StepEvaluator;
import io.inquisitor.harness.evaluation.StepReviewer;
import io.inquisitor.harness.evaluation.SupervisedStepRunner;
import io.inquisitor.harness.evaluation.logging.EvaluationLoggerCallback;
import io.inquisitor.harness.evaluation.logging.MarkdownEvaluationLogger;
import io.inquisitor.harness.evaluation.logging.PlainEvaluationLogger;
import io.inquisitor.harness.evaluation.report.EvaluationReportSession;
import io.inquisitor.harness.evaluation.report.EvaluationRunInfo;
import io.inquisitor.harness.executor.LlmStepRunner;
import io.inquisitor.harness.executor.StepRunner;
import io.inquisitor.harness.logging.ModelMetadataAdvisor;
import io.inquisitor.harness.logging.ModelRegistry;
import io.inquisitor.harness.logging.ModelRole;
import lombok.val;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * Wires one of two exclusive judge modes when evaluation is enabled. Observation-only
 * mode creates a {@link StepEvaluator} and an {@link EvaluationStepRunner}; refinement
 * mode creates a {@link StepReviewer} and a {@link SupervisedStepRunner}, whose final
 * review is also the evaluation result. Both modes record tool calls and evaluation
 * results. When disabled — or when this starter is absent — the harness runs the plain
 * {@link LlmStepRunner} with undecorated tools.
 *
 * <p>The judge is an OpenAI-compatible model configured from
 * {@code inquisitor.harness.evaluation.{model,baseUrl,apiKey}}; {@code baseUrl}/{@code apiKey}
 * fall back to the actor's {@code spring.ai.openai.*} settings. It should be a different
 * model from the actor — a self-judge shares its failure modes.
 */
@AutoConfiguration(after = InquisitorHarnessAutoConfiguration.class)
@ConditionalOnClass({ChatClient.class, Evaluator.class, OpenAiChatModel.class})
@Conditional(InquisitorEvaluationAutoConfiguration.JudgeModeEnabled.class)
@EnableConfigurationProperties(EvaluationProperties.class)
public class  InquisitorEvaluationAutoConfiguration {

    /** Activate when either mutually exclusive judge mode is requested. */
    static class JudgeModeEnabled extends AnyNestedCondition {
        JudgeModeEnabled() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "inquisitor.harness.evaluation", name = "enabled", havingValue = "true")
        static class Observation { }

        @ConditionalOnProperty(prefix = "inquisitor.harness.evaluation.refinement", name = "enabled", havingValue = "true")
        static class Refinement { }
    }

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
                if (bean instanceof ToolCallbackProvider provider) {
                    return (ToolCallbackProvider) () ->
                            Arrays.stream(provider.getToolCallbacks())
                                    .map(callback -> callback instanceof RecordingToolCallback
                                            ? callback : new RecordingToolCallback(callback))
                                    .toArray(ToolCallback[]::new);
                }
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
    EvaluationLoggerCallback inquisitorEvaluationLoggerCallback(
            InquisitorHarnessProperties properties,
            ModelRegistry models) {
        return switch (properties.logging().format()) {
            case PLAIN -> new PlainEvaluationLogger();
            case MARKDOWN -> new MarkdownEvaluationLogger(models);
        };
    }

    /**
     * Report wiring, active only when the optional {@code inquisitor-harness-evaluation-report}
     * module is on the classpath (the starter ships it by default; excluding it degrades
     * evaluation to score-only, no artifacts).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(EvaluationReportSession.class)
    static class EvaluationReportConfiguration {

        /**
         * Registers the recorder (and the model configuration) with the JVM-wide
         * {@link EvaluationReportSession}, so the report session listener can drain it
         * after the whole test plan. Registration is passive — a report is only written
         * when the listener finds {@code inquisitor.report.dir} set (the Gradle plugin's
         * {@code evaluate} task sets it).
         */
        @Bean
        InitializingBean inquisitorEvaluationReportRegistration(
                StepEvaluationRecorder recorder, EvaluationProperties properties, Environment environment) {
            return () -> EvaluationReportSession.register(recorder, new EvaluationRunInfo(
                    environment.getProperty("spring.ai.openai.chat.model"),
                    environment.getProperty("spring.ai.openai.base-url"),
                    properties.model(),
                    properties.baseUrl() != null
                            ? properties.baseUrl()
                            : environment.getProperty("spring.ai.openai.base-url")));
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "inquisitor.harness.evaluation.refinement",
            name = "enabled", havingValue = "false", matchIfMissing = true)
    Evaluator inquisitorStepEvaluator(
            EvaluationProperties properties,
            Environment environment,
            ModelRegistry models) {
        return new StepEvaluator(judgeClient(properties, environment, models));
    }

    @Bean
    @ConditionalOnMissingBean(StepReviewer.class)
    @ConditionalOnProperty(prefix = "inquisitor.harness.evaluation.refinement",
            name = "enabled", havingValue = "true")
    StepReviewer inquisitorStepReviewer(
            EvaluationProperties properties, Environment environment, ModelRegistry models) {
        return new LlmStepReviewer(judgeClient(properties, environment, models));
    }

    private static ChatClient judgeClient(EvaluationProperties properties,
            Environment environment, ModelRegistry models) {
        val baseUrl = properties.baseUrl() != null
                ? properties.baseUrl()
                : environment.getProperty("spring.ai.openai.base-url");
        val apiKey = properties.apiKey() != null
                ? properties.apiKey()
                : environment.getProperty("spring.ai.openai.api-key");
        Assert.hasText(properties.model(),
                "inquisitor.harness.evaluation.model must be set when evaluation or refinement is enabled");
        Assert.hasText(baseUrl, "No judge base URL: set inquisitor.harness.evaluation.base-url "
                + "or spring.ai.openai.base-url");
        Assert.hasText(apiKey, "No judge API key: set inquisitor.harness.evaluation.api-key "
                + "or spring.ai.openai.api-key");

        val judgeOptions = OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(properties.model())
                .temperature(0.0d)
                .build();

        val judgeModel = OpenAiChatModel.builder()
                .options(judgeOptions)
                .build();
        // A bare judge: no harness system prompt, no chat memory, no tools — it rules on
        // the trace it is given, nothing else.
        return ChatClient.builder(judgeModel)
                .defaultAdvisors(
                        new ModelMetadataAdvisor(ModelRole.JUDGE, models),
                        new SimpleLoggerAdvisor())
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnBean(LlmStepRunner.class)
    @ConditionalOnProperty(prefix = "inquisitor.harness.evaluation.refinement",
            name = "enabled", havingValue = "false", matchIfMissing = true)
    StepRunner inquisitorEvaluationStepRunner(
            LlmStepRunner llmStepRunner,
            Evaluator evaluator,
            StepEvaluationRecorder recorder,
            EvaluationLoggerCallback loggerCallback,
            ObjectProvider<EvaluationStepRunnerCallback> callbacks) {
        return new EvaluationStepRunner(llmStepRunner, evaluator,
                evaluationCallback(recorder, loggerCallback, callbacks));
    }

    @Bean
    @Primary
    @ConditionalOnBean(LlmStepRunner.class)
    @ConditionalOnProperty(prefix = "inquisitor.harness.evaluation.refinement",
            name = "enabled", havingValue = "true")
    StepRunner inquisitorSupervisedStepRunner(
            LlmStepRunner llmStepRunner,
            StepReviewer reviewer,
            StepEvaluationRecorder recorder,
            EvaluationLoggerCallback loggerCallback,
            EvaluationProperties properties,
            ObjectProvider<EvaluationStepRunnerCallback> callbacks) {
        return new SupervisedStepRunner(llmStepRunner, reviewer,
                properties.refinement().maxCorrections(),
                evaluationCallback(recorder, loggerCallback, callbacks));
    }

    private static EvaluationStepRunnerCallback evaluationCallback(
            StepEvaluationRecorder recorder,
            EvaluationLoggerCallback loggerCallback,
            ObjectProvider<EvaluationStepRunnerCallback> callbacks) {
        val first = (EvaluationStepRunnerCallback) recorder;
        return callbacks.orderedStream()
                .filter(candidate -> candidate != recorder && candidate != loggerCallback)
                .reduce(first, EvaluationStepRunnerCallback::andThen)
                .andThen(loggerCallback);
    }
}
