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
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.val;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.AnnotationConsumer;
import org.junit.jupiter.params.support.ParameterDeclaration;
import org.junit.jupiter.params.support.ParameterDeclarations;
import org.junit.platform.commons.support.ReflectionSupport;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;

/**
 * Expands a parameterized {@link Scenario @Scenario} into one run per argument set.
 *
 * <p>Any JUnit argument source works — {@code @ValueSource}, {@code @CsvSource},
 * {@code @MethodSource}, a custom {@code @ArgumentsSource} — through the public
 * {@link ArgumentsProvider} contract. Each value is named after the method parameter in
 * its position (the class must be compiled with {@code -parameters}, as Spring Boot's
 * Gradle plugin does), falling back to {@code argument N}. The values reach the model as
 * a context block; the scenario text is never rewritten.
 *
 * <p>junit-jupiter-params is optional: without it on the classpath every scenario has a
 * single, unparameterized run.
 */
final class ScenarioArguments {

    private static final boolean PARAMS_PRESENT = ClassUtils.isPresent(
            "org.junit.jupiter.params.provider.ArgumentsSource", ScenarioArguments.class.getClassLoader());

    private ScenarioArguments() {
    }

    /**
     * One run of a parameterized scenario.
     *
     * @param label      shown in front of each step's sub-test name and appended to the
     *                   scenario name (logs, report): the argument set's name, or
     *                   {@code name=value} pairs
     * @param parameters the named values, in parameter order, as handed to the model
     * @param values     the raw values, resolved into the method's parameters
     */
    record Run(String label, Map<String, String> parameters, @Nullable Object[] values) {
    }

    /** The runs declared by the method's argument sources; empty when it has none. */
    static List<Run> runs(ExtensionContext context) {
        return PARAMS_PRESENT ? Sources.runs(context) : List.of();
    }

    /** Touches junit-jupiter-params types only once they are known to be present. */
    private static final class Sources {

        static List<Run> runs(ExtensionContext context) {
            val method = context.getRequiredTestMethod();
            // Spring's merged annotations find @ArgumentsSource both declared directly and
            // as a meta-annotation of @CsvSource & co., including repeated sources.
            val sources = MergedAnnotations.from(method).stream(ArgumentsSource.class).toList();
            val declarations = new MethodParameters(method);
            val runs = new ArrayList<Run>();
            for (val source : sources) {
                val provider = ReflectionSupport.newInstance(source.getClass("value"));
                // The annotation carrying @ArgumentsSource (e.g. the @CsvSource), not the root:
                // for a composed annotation the root is the custom one, which the provider's
                // AnnotationConsumer<CsvSource> cannot take. Synthesized, so attribute
                // overrides declared by the composed annotation apply.
                val consumed = source.getMetaSource();
                if (provider instanceof AnnotationConsumer<?> consumer && consumed != null) {
                    accept(consumer, consumed.synthesize());
                }
                try (val arguments = ((ArgumentsProvider) provider).provideArguments(declarations, context)) {
                    arguments.forEach(argument -> runs.add(run(method, argument)));
                } catch (Exception e) {
                    throw new IllegalStateException("Could not provide the arguments of scenario " + method.getName()
                            + " from @" + source.getRoot().getType().getSimpleName(), e);
                }
            }
            return runs;
        }

        @SuppressWarnings("unchecked")
        private static void accept(AnnotationConsumer<?> consumer, java.lang.annotation.Annotation annotation) {
            ((AnnotationConsumer<java.lang.annotation.Annotation>) consumer).accept(annotation);
        }

        private static Run run(Method method, Arguments argument) {
            val values = argument.get();
            val parameters = new LinkedHashMap<String, String>();
            for (int i = 0; i < values.length; i++) {
                parameters.put(name(method, i), String.valueOf(values[i]));
            }
            val label = argument instanceof Arguments.ArgumentSet set
                    ? set.getName()
                    : parameters.entrySet().stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining(", "));
            return new Run(label, parameters, values);
        }

        private static String name(Method method, int index) {
            val parameters = method.getParameters();
            return index < parameters.length && parameters[index].isNamePresent()
                    ? parameters[index].getName()
                    : "argument " + (index + 1);
        }
    }

    /** The method's parameters, as argument providers expect to see them declared. */
    private record MethodParameters(Method method) implements ParameterDeclarations {

        @Override
        public List<ParameterDeclaration> getAll() {
            val parameters = method.getParameters();
            return IntStream.range(0, parameters.length)
                    .<ParameterDeclaration>mapToObj(i -> new Declaration(parameters[i], i))
                    .toList();
        }

        @Override
        public Optional<ParameterDeclaration> getFirst() {
            return get(0);
        }

        @Override
        public Optional<ParameterDeclaration> get(int index) {
            val all = getAll();
            return index >= 0 && index < all.size() ? Optional.of(all.get(index)) : Optional.empty();
        }

        @Override
        public AnnotatedElement getSourceElement() {
            return method;
        }

        @Override
        public String getSourceElementDescription() {
            return "method [" + method.getName() + "(" + Arrays.stream(method.getParameterTypes())
                    .map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")]";
        }
    }

    private record Declaration(Parameter parameter, int index) implements ParameterDeclaration {

        @Override
        public AnnotatedElement getAnnotatedElement() {
            return parameter;
        }

        @Override
        public Class<?> getParameterType() {
            return parameter.getType();
        }

        @Override
        public int getParameterIndex() {
            return index;
        }

        @Override
        public Optional<String> getParameterName() {
            return parameter.isNamePresent() ? Optional.of(parameter.getName()) : Optional.empty();
        }
    }
}
