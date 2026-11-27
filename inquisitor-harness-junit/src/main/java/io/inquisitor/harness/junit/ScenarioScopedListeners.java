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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Gives a {@link Scenario @Scenario} one Spring "test method" lifecycle for the whole
 * scenario rather than one per step.
 *
 * <p>A {@code @Scenario} is a {@code @TestTemplate} with one invocation per step, and
 * Spring's TestContext framework treats every invocation as its own test method. For
 * the listeners whose method-phase work is about the <em>test</em> as a unit, that is
 * wrong for a scenario: {@code @Sql} would re-seed data between steps,
 * {@code @DirtiesContext} would restart the application mid-scenario, Mockito bean
 * mocks would be reset between steps, and {@code @RecordApplicationEvents} would only
 * keep the last step's events. This decorates those listeners in the class's
 * {@link TestContextManager}: for the scenario method being run, their
 * {@code beforeTestMethod} runs ahead of the first step only and their
 * {@code afterTestMethod} after the last step only, and the per-step test instances
 * after the first are not re-prepared by them. Class phases, per-execution phases, and
 * ordinary test methods are delegated unchanged.
 *
 * <p>A test-managed transaction ({@code @Transactional}) is rejected instead: the
 * application under test serves requests on its own threads, so it can never see the
 * test's uncommitted data, and the harness's SQL statements (run on the test thread)
 * would be rolled back behind the scenario's back.
 */
final class ScenarioScopedListeners {

    /** By name, so no optional module (spring-jdbc, Mockito) is loaded to compare types. */
    private static final Set<String> SCENARIO_SCOPED = Set.of(
            "org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener",
            "org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener",
            "org.springframework.test.context.support.DirtiesContextTestExecutionListener",
            "org.springframework.test.context.bean.override.mockito.MockitoResetTestExecutionListener",
            "org.springframework.test.context.event.ApplicationEventsTestExecutionListener",
            "org.springframework.test.context.event.EventPublishingTestExecutionListener");

    private static final String SPRING_TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";
    private static final String JAKARTA_TRANSACTIONAL = "jakarta.transaction.Transactional";
    /** Propagation / TxType values that suspend rather than open a transaction. */
    private static final Set<String> NON_TRANSACTIONAL = Set.of("NOT_SUPPORTED", "NEVER");

    private static final String PROGRESS = ScenarioScopedListeners.class.getName() + ".progress";

    private ScenarioScopedListeners() {
    }

    /**
     * Scopes the method-phase listeners to the scenario method of {@code context}, whose
     * {@code steps} invocations are about to run. Call after the Spring context is loaded
     * (the {@link TestContextManager} then exists).
     *
     * @throws IllegalStateException when the scenario would run in a test-managed transaction
     */
    static void scopeToScenario(ExtensionContext context, int steps) {
        val method = context.getRequiredTestMethod();
        rejectTransactional(method, context.getRequiredTestClass());

        val manager = testContextManager(context);
        if (manager == null) {
            throw new IllegalStateException("Cannot scope the Spring test lifecycle to scenario " + method.getName()
                    + ": the Spring TestContextManager was not found (unsupported spring-test version?)");
        }
        val listeners = manager.getTestExecutionListeners();
        for (int i = 0; i < listeners.size(); i++) {
            val listener = listeners.get(i);
            if (SCENARIO_SCOPED.contains(listener.getClass().getName())) {
                listeners.set(i, new ScenarioScoped(listener));
            }
        }
        manager.getTestContext().setAttribute(PROGRESS, new Progress(method, steps));
    }

    /**
     * The manager {@link SpringExtension} keeps per test class in the root store; it has
     * no public accessor, but {@link TestContextManager#getTestExecutionListeners()} is
     * documented as modifiable for exactly this kind of adjustment.
     */
    private static @Nullable TestContextManager testContextManager(ExtensionContext context) {
        return context.getRoot()
                .getStore(ExtensionContext.Namespace.create(SpringExtension.class))
                .get(context.getRequiredTestClass(), TestContextManager.class);
    }

    private static void rejectTransactional(Method method, Class<?> testClass) {
        // The method's own declaration wins (e.g. @Transactional(propagation = NOT_SUPPORTED)
        // opting a scenario out of a class-level @Transactional), as in Spring.
        // TYPE_HIERARCHY on the method also finds a declaration on a method it overrides (in a
        // superclass or interface), as Spring's transaction attribute lookup does.
        val transactional = opensTransaction(MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY))
                .or(() -> classOpensTransaction(testClass))
                .orElse(false);
        if (transactional) {
            throw new IllegalStateException("@Transactional is not supported on @Scenario " + method.getName()
                    + ": the application under test runs on its own server threads and never sees a test-managed "
                    + "transaction, and the harness's SQL statements would be rolled back after the scenario. "
                    + "Seed data with @Sql (committed) and clean up with "
                    + "@Sql(executionPhase = AFTER_TEST_METHOD) instead.");
        }
    }

    /**
     * The class's transactional declaration: its own (or a superclass's), else — for a
     * {@code @Nested} class that inherits its enclosing class's test configuration, as
     * Spring does by default — the enclosing class's, outwards.
     */
    private static Optional<Boolean> classOpensTransaction(Class<?> testClass) {
        return opensTransaction(MergedAnnotations.from(testClass, SearchStrategy.TYPE_HIERARCHY))
                .or(() -> TestContextAnnotationUtils.searchEnclosingClass(testClass)
                        ? classOpensTransaction(testClass.getEnclosingClass())
                        : Optional.empty());
    }

    /** Whether the nearest transactional annotation opens a transaction; empty when there is none. */
    private static Optional<Boolean> opensTransaction(MergedAnnotations annotations) {
        return attribute(annotations.get(SPRING_TRANSACTIONAL), "propagation")
                .or(() -> attribute(annotations.get(JAKARTA_TRANSACTIONAL), "value"))
                .map(mode -> !NON_TRANSACTIONAL.contains(mode));
    }

    private static Optional<String> attribute(MergedAnnotation<?> annotation, String name) {
        return annotation.isPresent()
                ? annotation.getValue(name).map(String::valueOf)
                : Optional.empty();
    }

    /** The scenario method whose steps are running, and how many there are. */
    private record Progress(Method method, int steps) {
    }

    /**
     * Delegates to a Spring listener, running its method phases at scenario boundaries.
     * Each decorator counts the steps itself, keyed to the {@link Progress} instance, so
     * several decorators share one marker without interfering.
     */
    private static final class ScenarioScoped implements TestExecutionListener, Ordered {

        private final TestExecutionListener delegate;
        private @Nullable Progress progress;
        private int begun;
        private int ended;

        ScenarioScoped(TestExecutionListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void beforeTestClass(TestContext testContext) throws Exception {
            delegate.beforeTestClass(testContext);
        }

        /**
         * Skipped while a scenario is between its first and last step: with JUnit's default
         * per-method lifecycle each step gets a new test instance, and e.g. the
         * {@code @RecordApplicationEvents} listener would start a fresh, empty recording for
         * it. (Instance injection itself is not one of these listeners and still happens.)
         */
        @Override
        public void prepareTestInstance(TestContext testContext) throws Exception {
            if (!inProgress(testContext)) {
                delegate.prepareTestInstance(testContext);
            }
        }

        @Override
        public void beforeTestMethod(TestContext testContext) throws Exception {
            val current = current(testContext);
            if (current == null) {
                delegate.beforeTestMethod(testContext);
                return;
            }
            if (current != progress) {
                // Identity, not equality: a fresh marker means a fresh scenario run.
                progress = current;
                begun = 0;
                ended = 0;
            }
            if (begun++ == 0) {
                delegate.beforeTestMethod(testContext);
            }
        }

        @Override
        public void beforeTestExecution(TestContext testContext) throws Exception {
            delegate.beforeTestExecution(testContext);
        }

        @Override
        public void afterTestExecution(TestContext testContext) throws Exception {
            delegate.afterTestExecution(testContext);
        }

        @Override
        public void afterTestMethod(TestContext testContext) throws Exception {
            val current = current(testContext);
            if (current == null) {
                delegate.afterTestMethod(testContext);
            } else if (current == progress && ++ended == current.steps()) {
                delegate.afterTestMethod(testContext);
            }
        }

        @Override
        public void afterTestClass(TestContext testContext) throws Exception {
            delegate.afterTestClass(testContext);
        }

        @Override
        public int getOrder() {
            return delegate instanceof Ordered ordered ? ordered.getOrder() : Ordered.LOWEST_PRECEDENCE;
        }

        /** Whether the scenario this decorator last began is still between its first and last step. */
        private boolean inProgress(TestContext testContext) {
            return progress != null
                    && testContext.getAttribute(PROGRESS) == progress
                    && begun > 0
                    && ended < progress.steps();
        }

        /** The progress of the scenario currently running, if this method is one. */
        private static @Nullable Progress current(TestContext testContext) {
            return testContext.getAttribute(PROGRESS) instanceof Progress current
                    && current.method().equals(testContext.getTestMethod())
                    ? current
                    : null;
        }
    }
}
