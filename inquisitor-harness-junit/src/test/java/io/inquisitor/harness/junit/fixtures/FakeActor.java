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

package io.inquisitor.harness.junit.fixtures;

import java.time.Duration;
import java.util.List;

import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.StepVerdict;
import io.inquisitor.harness.parser.ScenarioParser;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The beans a {@code @Harness} suite needs, with an LLM-free stand-in for the actor: every
 * step is handed to the context's {@link FakeStep} beans (each test contributes what a step
 * should do or observe) and then passes. A plain Spring context — no Spring Boot, no model.
 */
@Configuration(proxyBeanMethods = false)
public class FakeActor {

    /** What the fake actor does at a step. */
    @FunctionalInterface
    public interface FakeStep {
        void act(StepRequest request);
    }

    @Bean
    ScenarioParser scenarioParser() {
        return new ScenarioParser();
    }

    /** Where {@code HarnessExtension} registers the application's HTTP target. */
    @Bean
    HttpTargetRegistry httpTargetRegistry() {
        return new HttpTargetRegistry();
    }

    @Bean
    ScenarioExecutor scenarioExecutor(ObjectProvider<FakeStep> steps) {
        return new ScenarioExecutor(request -> {
            steps.orderedStream().forEach(step -> step.act(request));
            return new StepRun(new StepVerdict(Outcome.PASS, "fake actor", List.of()), List.of(), Duration.ZERO);
        });
    }
}
