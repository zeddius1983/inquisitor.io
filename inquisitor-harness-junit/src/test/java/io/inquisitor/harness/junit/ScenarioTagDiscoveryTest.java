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

package io.inquisitor.harness.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/**
 * Pins the selection contract behind the Gradle plugin's {@code evaluate} task:
 * {@link Scenario @Scenario} is meta-annotated {@code @Tag("inquisitor")}, so a JUnit
 * Platform run with {@code includeTags("inquisitor")} selects every scenario method —
 * with no tag annotation on the test class — and nothing else.
 */
class ScenarioTagDiscoveryTest {

    @Test
    void includeTagsInquisitorSelectsScenarioMethodsOnly() {
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(SampleSuite.class))
                .filters(TagFilter.includeTags("inquisitor"))
                .build();

        var testPlan = LauncherFactory.create().discover(request);
        var displayNames = testPlan.getRoots().stream()
                .flatMap(root -> testPlan.getDescendants(root).stream())
                .map(TestIdentifier::getDisplayName)
                .toList();

        assertThat(displayNames)
                .anyMatch(name -> name.contains("someScenario"))
                .anyMatch(name -> name.contains("expectedToFail"))
                .noneMatch(name -> name.contains("plainTest"));
    }

    /**
     * Discovery-only fixture. {@code @Disabled} keeps the build's own test scanning
     * from executing it (a {@code @Scenario} without a Spring context would fail);
     * disabling affects execution, not the discovery this test exercises.
     */
    @Disabled("discovery-only fixture")
    static class SampleSuite {

        @Scenario
        void someScenario() {}

        @Scenario(expect = Expect.FAIL)
        void expectedToFail() {}

        @Test
        void plainTest() {}
    }
}
