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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.inquisitor.harness.executor.ScenarioExecution;
import io.inquisitor.harness.executor.ScenarioExecutor;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepResult;
import io.inquisitor.harness.parser.ScenarioParser;
import lombok.val;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;
import org.opentest4j.TestAbortedException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Drives a {@link io.inquisitor.harness.junit.Scenario @Scenario} method as a JUnit
 * {@code @TestTemplate}: it parses the scenario markdown and emits one invocation
 * (sub-test) per {@code ## Step}, all sharing a single {@link ScenarioExecution} so
 * an id created in step 1 flows on via chat memory. Evaluation is fail-fast — once a
 * step fails, the remaining steps are reported as skipped.
 */
public class ScenarioTemplateProvider implements TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> AnnotationSupport.isAnnotated(
                        method, io.inquisitor.harness.junit.Scenario.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        val applicationContext = SpringExtension.getApplicationContext(context);
        val parser = applicationContext.getBean(ScenarioParser.class);
        val executor = applicationContext.getBean(ScenarioExecutor.class);

        val expect = AnnotationSupport.findAnnotation(
                        context.getRequiredTestMethod(), io.inquisitor.harness.junit.Scenario.class)
                .map(io.inquisitor.harness.junit.Scenario::expect)
                .orElse(Expect.PASS);
        val loaded = load(applicationContext, parser, resolveLocation(context));
        // Carry the expectation into the model so downstream consumers (the evaluation
        // report's expectation gate) see how this run defines success.
        val scenario = expect == Expect.FAIL ? loaded.withExpectedOutcome(Outcome.FAIL) : loaded;
        // One execution shared by every step invocation of this method; they run
        // sequentially, so each invocation advances the same conversation in order.
        val state = new ScenarioState(executor.start(scenario), expect);

        return scenario.steps().stream().map(step -> new StepInvocationContext(step, state));
    }

    private static String resolveLocation(ExtensionContext context) {
        val method = context.getRequiredTestMethod();
        val explicit = AnnotationSupport.findAnnotation(method, io.inquisitor.harness.junit.Scenario.class)
                .map(io.inquisitor.harness.junit.Scenario::value)
                .orElse("");
        if (!explicit.isBlank()) {
            return explicit;
        }
        val dir = AnnotationSupport.findAnnotation(context.getRequiredTestClass(), Harness.class)
                .map(Harness::scenarioDir)
                .orElse("classpath:scenarios/");
        val base = dir.endsWith("/") ? dir : dir + "/";
        return base + kebab(method.getName()) + ".md";
    }

    private static Scenario load(ApplicationContext applicationContext, ScenarioParser parser, String location) {
        val resource = applicationContext.getResource(location);
        try {
            // The full location (not resource.getFilename()) so Scenario.source keeps the
            // directory — the style bucket the evaluation report groups by.
            return parser.parse(resource.getContentAsString(StandardCharsets.UTF_8), location);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read scenario " + location, e);
        }
    }

    /** {@code transferBetweenAccounts} → {@code transfer-between-accounts}. */
    private static String kebab(String methodName) {
        return methodName.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /** Mutable per-scenario state shared across the step invocations. */
    private static final class ScenarioState {
        private final ScenarioExecution execution;
        private final Expect expect;
        /** Set once no further steps should run: a step failed (PASS), or the expected failure landed (FAIL). */
        private boolean done;

        ScenarioState(ScenarioExecution execution, Expect expect) {
            this.execution = execution;
            this.expect = expect;
        }
    }

    private record StepInvocationContext(Step step, ScenarioState state) implements TestTemplateInvocationContext {

        @Override
        public String getDisplayName(int invocationIndex) {
            return step.title().isBlank() ? "Step " + step.index() : step.title();
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return List.of(new StepExecution(state));
        }
    }

    /** Executes the next step before the (empty) test body, failing or skipping accordingly. */
    private record StepExecution(ScenarioState state) implements BeforeTestExecutionCallback {

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            if (state.done) {
                throw new TestAbortedException(state.expect == Expect.FAIL
                        ? "skipped: the scenario already failed as expected at an earlier step"
                        : "skipped: an earlier step in this scenario failed");
            }
            StepResult result;
            try {
                result = state.execution.next();
            } catch (RuntimeException e) {
                // An infrastructure error (not a verdict): without this, the execution's
                // cursor hasn't advanced and the *next* sub-test would silently re-run this
                // step under the wrong name, with the last step never running at all.
                state.done = true;
                throw e;
            }

            if (state.expect == Expect.FAIL) {
                if (!result.passed()) {
                    // The expected failure landed — this step is the success; stop here.
                    state.done = true;
                    return;
                }
                // This step passed; once it was the last one, the oracle never caught the fault.
                // (Safe to read hasNext() here: the step passed, so the execution is not failed.)
                if (!state.execution.hasNext()) {
                    state.done = true;
                    throw new AssertionError("Expected this scenario to FAIL, but every step passed — "
                            + "the oracle did not catch the fault.\n" + describe(result));
                }
                return;
            }

            if (!result.passed()) {
                state.done = true;
                throw new AssertionError(describe(result));
            }
        }

        private static String describe(StepResult result) {
            val verdict = result.verdict();
            val message = new StringBuilder("[").append(verdict.outcome()).append("] ")
                    .append(result.step().title()).append(" — ").append(verdict.reasoning());
            if (!verdict.evidence().isEmpty()) {
                message.append("\n      evidence: ").append(verdict.evidence());
            }
            return message.toString();
        }
    }
}
