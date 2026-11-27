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

package io.inquisitor.demo.fixtures;

import java.time.Duration;
import java.util.List;

import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.StepVerdict;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * An LLM-free stand-in for the actor, for tests of the harness's JUnit plumbing rather
 * than of a model: every step is handed to the context's {@link FakeStep} beans (each
 * test contributes what a step should do or observe) and then passes.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeActor {

    /** What the fake actor does at a step. */
    @FunctionalInterface
    public interface FakeStep {
        void act(StepRequest request);
    }

    /** Replaces the LLM-backed executor of the harness starter. */
    @Bean
    ScenarioExecutor scenarioExecutor(ObjectProvider<FakeStep> steps) {
        return new ScenarioExecutor(request -> {
            steps.orderedStream().forEach(step -> step.act(request));
            return new StepRun(new StepVerdict(Outcome.PASS, "fake actor", List.of()), List.of(), Duration.ZERO);
        });
    }
}
