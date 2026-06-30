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

import io.inquisitor.harness.evaluation.CredibilityEvaluationStepRunner;
import io.inquisitor.harness.evaluation.CredibilityRecorder;
import io.inquisitor.harness.executor.LlmStepRunner;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepRunner;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.evaluation.Evaluator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InquisitorEvaluationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    InquisitorHarnessAutoConfiguration.class, InquisitorEvaluationAutoConfiguration.class))
            .withBean(ChatModel.class, () -> mock(ChatModel.class));

    @Test
    void evaluationIsOffByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CredibilityRecorder.class);
            assertThat(context).doesNotHaveBean(Evaluator.class);
            assertThat(context.getBean(StepRunner.class)).isInstanceOf(LlmStepRunner.class);
            assertThat(context).hasSingleBean(ScenarioExecutor.class);
        });
    }

    @Test
    void wrapsTheRunnerWithCredibilityEvaluationWhenEnabled() {
        runner.withPropertyValues(
                        "inquisitor.harness.evaluation.enabled=true",
                        "inquisitor.harness.evaluation.model=judge-model",
                        "inquisitor.harness.evaluation.base-url=http://localhost:9999",
                        "inquisitor.harness.evaluation.api-key=test-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(CredibilityRecorder.class);
                    assertThat(context).hasSingleBean(Evaluator.class);
                    // The @Primary wrapper is what the executor resolves.
                    assertThat(context.getBean(StepRunner.class)).isInstanceOf(CredibilityEvaluationStepRunner.class);
                    assertThat(context).hasSingleBean(ScenarioExecutor.class);
                });
    }

    @Test
    void failsFastWhenEnabledWithoutAJudgeModel() {
        runner.withPropertyValues("inquisitor.harness.evaluation.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
