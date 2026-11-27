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

import java.lang.reflect.Method;

import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Makes Spring's {@link Sql @Sql} fixtures run once per {@link Scenario @Scenario}
 * rather than once per step.
 *
 * <p>A {@code @Scenario} is a {@code @TestTemplate} with one invocation per step, and
 * Spring's TestContext framework treats every invocation as its own test method, so
 * {@code SqlScriptsTestExecutionListener} would run {@code BEFORE_TEST_METHOD} scripts
 * before <em>each</em> step (re-seeding the data mid-scenario) and
 * {@code AFTER_TEST_METHOD} scripts after each one. The scenario is the test, so this
 * decorates that listener in the class's {@link TestContextManager}: for the scenario
 * method being run, the before-scripts run ahead of the first step only and the
 * after-scripts after the last step only. Class-phase scripts and ordinary test
 * methods are delegated unchanged.
 */
final class ScenarioSqlScripts {

    /** By name, so this class never loads Spring JDBC types when spring-jdbc is absent. */
    private static final String SQL_LISTENER = "org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener";
    private static final String PROGRESS = ScenarioSqlScripts.class.getName() + ".progress";

    private ScenarioSqlScripts() {
    }

    /**
     * Scopes {@code @Sql} fixtures to the scenario method of {@code context}, whose
     * {@code steps} invocations are about to run. Call after the Spring context is
     * loaded (the {@link TestContextManager} then exists).
     */
    static void scopeToScenario(ExtensionContext context, int steps) {
        val method = context.getRequiredTestMethod();
        val manager = testContextManager(context);
        if (manager == null) {
            if (declaresSql(context)) {
                throw new IllegalStateException("Cannot scope @Sql fixtures to scenario " + method.getName()
                        + ": the Spring TestContextManager was not found (unsupported spring-test version?)");
            }
            return;
        }
        val listeners = manager.getTestExecutionListeners();
        for (int i = 0; i < listeners.size(); i++) {
            val listener = listeners.get(i);
            if (listener.getClass().getName().equals(SQL_LISTENER)) {
                listeners.set(i, new OncePerScenario(listener));
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

    private static boolean declaresSql(ExtensionContext context) {
        return MergedAnnotations.from(context.getRequiredTestMethod()).isPresent(Sql.class)
                || MergedAnnotations.from(context.getRequiredTestClass(), SearchStrategy.TYPE_HIERARCHY)
                        .isPresent(Sql.class);
    }

    /** How far the step invocations of one scenario method have got. */
    private static final class Progress {
        private final Method method;
        private final int steps;
        private int begun;
        private int ended;

        Progress(Method method, int steps) {
            this.method = method;
            this.steps = steps;
        }

        /** Whether this is the scenario's first step starting. */
        boolean firstBegins() {
            return begun++ == 0;
        }

        /** Whether this is the scenario's last step ending. */
        boolean lastEnds() {
            return ++ended == steps;
        }
    }

    /** Delegates to Spring's SQL listener, gating the method phases to scenario boundaries. */
    private record OncePerScenario(TestExecutionListener delegate) implements TestExecutionListener, Ordered {

        @Override
        public void beforeTestClass(TestContext testContext) throws Exception {
            delegate.beforeTestClass(testContext);
        }

        @Override
        public void prepareTestInstance(TestContext testContext) throws Exception {
            delegate.prepareTestInstance(testContext);
        }

        @Override
        public void beforeTestMethod(TestContext testContext) throws Exception {
            val progress = progress(testContext);
            if (progress == null || progress.firstBegins()) {
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
            val progress = progress(testContext);
            if (progress == null) {
                delegate.afterTestMethod(testContext);
            } else if (progress.lastEnds()) {
                testContext.removeAttribute(PROGRESS);
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

        /** The progress of the scenario currently running, if this method is one. */
        private static @Nullable Progress progress(TestContext testContext) {
            return testContext.getAttribute(PROGRESS) instanceof Progress progress
                    && progress.method.equals(testContext.getTestMethod())
                    ? progress
                    : null;
        }
    }
}
