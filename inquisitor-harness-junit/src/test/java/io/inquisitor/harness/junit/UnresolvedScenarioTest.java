/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.inquisitor.harness.junit;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.executor.UnresolvedStepException;
import io.inquisitor.harness.parser.ScenarioParser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

class UnresolvedScenarioTest {
    @Test
    void unresolvedExecutionFailsEvenWhenApplicationFailureWasExpected() {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(UnresolvedFixture.class))
                .configurationParameter("junit.jupiter.conditions.deactivate",
                        "org.junit.jupiter.engine.extension.DisabledCondition")
                .build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        assertThat(summary.getTestsFailedCount()).isEqualTo(1);
        assertThat(summary.getTestsSucceededCount()).isZero();
        assertThat(summary.getTestsAbortedCount()).isEqualTo(1);
        assertThat(summary.getFailures().getFirst().getException()).isInstanceOf(UnresolvedStepException.class);
    }

    @Disabled("Executed explicitly by the launcher test")
    @ExtendWith(SpringExtension.class)
    @ContextConfiguration(classes = FixtureConfiguration.class)
    static class UnresolvedFixture {
        @Scenario(value = "classpath:scenarios/unresolved.md", expect = Expect.FAIL)
        void faultDetection() { }
    }

    @Configuration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean ScenarioParser parser() { return new ScenarioParser(); }
        @Bean ScenarioExecutor executor() {
            return new ScenarioExecutor(request -> {
                throw new UnresolvedStepException("Correction budget exhausted", List.of(), null);
            });
        }
    }
}
