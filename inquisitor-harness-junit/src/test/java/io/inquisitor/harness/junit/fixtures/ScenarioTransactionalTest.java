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

import static org.assertj.core.api.Assertions.assertThat;

import io.inquisitor.harness.junit.Scenario;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pins that a test-managed transaction is rejected on a {@link Scenario @Scenario} — the
 * app under test serves requests on its own threads and would never see the test's
 * uncommitted data — while a scenario that opts out with
 * {@code @Transactional(propagation = NOT_SUPPORTED)} runs normally.
 */
class ScenarioTransactionalTest {

    static volatile boolean launched;

    @Test
    void transactionalScenarioIsRejected() {
        var results = launch(TransactionalSuite.class);

        assertThat(results.get("rejected").getThrowable())
                .hasValueSatisfying(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("@Transactional is not supported on @Scenario rejected"));
        assertThat(results.get("optedOut").getStatus()).isEqualTo(TestExecutionResult.Status.SUCCESSFUL);
        // A @Nested class inherits the enclosing class's @Transactional, as in Spring.
        assertThat(results.get("nestedRejected").getThrowable())
                .hasValueSatisfying(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("@Transactional is not supported on @Scenario nestedRejected"));
    }

    /** Runs the suite and returns each scenario method's result, keyed by method name. */
    private static Map<String, TestExecutionResult> launch(Class<?> suite) {
        var results = new ConcurrentHashMap<String, TestExecutionResult>();
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(suite))
                .build();
        launched = true;
        try {
            LauncherFactory.create().execute(request, new TestExecutionListener() {
                @Override
                public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
                    // The scenario methods themselves (templates), not their step sub-tests.
                    if (identifier.isContainer() && identifier.getDisplayName().endsWith("()")) {
                        results.put(identifier.getDisplayName().replace("()", ""), result);
                    }
                }
            });
        } finally {
            launched = false;
        }
        return results;
    }

    static boolean launched() {
        return launched;
    }

    /** Runs only when launched by the test above, not by the build's own test scan. */
    @FakeActorTest
    @Transactional
    @EnabledIf("io.inquisitor.harness.junit.fixtures.ScenarioTransactionalTest#launched")
    static class TransactionalSuite {

        @Configuration(proxyBeanMethods = false)
        @Import(FakeActor.class)
        static class Config {
        }

        @Scenario("classpath:fixtures/three-steps.md")
        void rejected() {}

        @Scenario("classpath:fixtures/three-steps.md")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void optedOut() {}

        @Nested
        class Inner {

            @Scenario("classpath:fixtures/three-steps.md")
            void nestedRejected() {}
        }
    }
}
