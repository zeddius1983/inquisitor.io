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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import io.inquisitor.harness.junit.Scenario;
import io.inquisitor.harness.junit.fixtures.FakeActor.FakeStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

/**
 * Pins parameterized scenarios: a {@link Scenario @Scenario} with a JUnit argument source
 * runs once per argument set; each run's values reach the model as a context block named
 * after the method parameters (the scenario text is untouched), resolve into the
 * method's typed parameters, and the per-test lifecycle — here {@link Sql @Sql} and
 * {@link BeforeEach @BeforeEach} — brackets each run.
 */
@FakeActorTest
class ScenarioParametersTest {

    private static final String SEED = "'scenario-seed'";

    /** One step as the fake actor saw it. */
    record Seen(String scenario, String message, long seeds) {}

    static final List<Seen> SEEN = new CopyOnWriteArrayList<>();
    static final List<Object> RESOLVED = new CopyOnWriteArrayList<>();
    static final AtomicInteger BEFORE_EACH = new AtomicInteger();

    /** Takes a parameter of its own: run values must only go to the scenario method. */
    @BeforeEach
    void countRuns(TestInfo test) {
        assertThat(test.getTestMethod()).isPresent();
        BEFORE_EACH.incrementAndGet();
    }

    @Scenario("classpath:fixtures/three-steps.md")
    @CsvSource({"EUR, 100.00", "GBP, 0.01"})
    @Sql("/fixtures/scenario-seed.sql")
    void csvRows(String currency, BigDecimal amount) {
        RESOLVED.add(amount);
    }

    @Scenario("classpath:fixtures/three-steps.md")
    @MethodSource("namedSets")
    void namedArgumentSets(String owner, int count) {}

    static Stream<Arguments> namedSets() {
        return Stream.of(argumentSet("a single owner", "Ivan", 1));
    }

    /** A composed argument source: a custom annotation meta-annotated with @CsvSource. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @CsvSource({"CHF, 5.00"})
    @interface SwissFrancRow {
    }

    @Scenario("classpath:fixtures/three-steps.md")
    @SwissFrancRow
    void composedSource(String currency, BigDecimal amount) {}

    @AfterAll
    static void eachArgumentSetIsOneRun() {
        var csv = SEEN.stream()
                .filter(seen -> seen.scenario().matches("Three steps \\[currency=(EUR|GBP), .*")).toList();
        assertThat(csv).extracting(Seen::scenario).containsExactly(
                "Three steps [currency=EUR, amount=100.00]", "Three steps [currency=EUR, amount=100.00]",
                "Three steps [currency=EUR, amount=100.00]", "Three steps [currency=GBP, amount=0.01]",
                "Three steps [currency=GBP, amount=0.01]", "Three steps [currency=GBP, amount=0.01]");
        assertThat(csv.subList(0, 3)).extracting(Seen::message)
                .allSatisfy(message -> assertThat(message)
                        .startsWith("Parameters for this run:\n- currency: EUR\n- amount: 100.00\n\n"));
        assertThat(csv.subList(3, 6)).extracting(Seen::message)
                .allSatisfy(message -> assertThat(message)
                        .startsWith("Parameters for this run:\n- currency: GBP\n- amount: 0.01\n\n"));
        // The step text itself is untouched: the instruction follows the block verbatim.
        assertThat(csv.getFirst().message()).endsWith("Step 1: Step 1 — First\nDo the first thing.");

        // @Sql seeds once per run, not per step (per step it would read 1, 2, 3, 4, 5, 6).
        assertThat(csv).extracting(Seen::seeds).containsExactly(1L, 1L, 1L, 2L, 2L, 2L);

        // Typed method parameters, converted from the CSV text (resolved for every step).
        assertThat(RESOLVED).hasSize(6).allSatisfy(value -> assertThat(value).isInstanceOf(BigDecimal.class));

        var named = SEEN.stream().filter(seen -> seen.scenario().equals("Three steps [a single owner]")).toList();
        assertThat(named).hasSize(3).extracting(Seen::message)
                .allSatisfy(message -> assertThat(message)
                        .startsWith("Parameters for this run:\n- owner: Ivan\n- count: 1\n\n"));

        var composed = SEEN.stream()
                .filter(seen -> seen.scenario().equals("Three steps [currency=CHF, amount=5.00]")).toList();
        assertThat(composed).as("runs of the composed @CsvSource").hasSize(3);

        // Once per run: two CSV rows, one named set, one composed row.
        assertThat(BEFORE_EACH).hasValue(4);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FakeActor.class)
    static class Config {

        @Bean
        RecordingDataSource dataSource() {
            return new RecordingDataSource();
        }

        @Bean
        FakeStep observeRun(RecordingDataSource dataSource) {
            return request -> SEEN.add(
                    new Seen(request.scenario().name(), request.userMessage(), dataSource.count(SEED)));
        }
    }
}
