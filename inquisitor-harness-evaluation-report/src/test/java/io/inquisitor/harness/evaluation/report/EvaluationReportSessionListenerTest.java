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

package io.inquisitor.harness.evaluation.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.inquisitor.harness.evaluation.StepEvaluationRecorder;
import io.inquisitor.harness.executor.StepRequest;
import io.inquisitor.harness.executor.StepRun;
import io.inquisitor.harness.model.Outcome;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import io.inquisitor.harness.model.StepVerdict;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.evaluation.EvaluationResponse;

class EvaluationReportSessionListenerTest {

    @TempDir
    private Path dir;

    @AfterEach
    void cleanUp() {
        System.clearProperty(EvaluationReportSessionListener.REPORT_DIR_PROPERTY);
        System.clearProperty(EvaluationReportSessionListener.HEADER_PROPERTY);
        System.clearProperty(EvaluationReportSessionListener.FORMATS_PROPERTY);
        EvaluationReportSession.reset();
    }

    private static StepEvaluationRecorder recorderWithOneRecord() {
        val scenario = new Scenario("Ping", "", List.of(new Step(1, "Ping", "ping it")),
                "classpath:scenarios/explicit/ping.md");
        val recorder = new StepEvaluationRecorder();
        recorder.record(
                StepRequest.of("conv", scenario, scenario.steps().getFirst()),
                new StepRun(new StepVerdict(Outcome.PASS, "pong", List.of()), List.of(), Duration.ZERO),
                new EvaluationResponse(true, 1.0f, "", Map.of("category", "GROUNDED")));
        return recorder;
    }

    @Test
    void writesTheDefaultHtmlReportAtSessionClose() {
        EvaluationReportSession.register(recorderWithOneRecord(),
                new EvaluationRunInfo("actor", "http://a", "judge", "http://j"));
        System.setProperty(EvaluationReportSessionListener.REPORT_DIR_PROPERTY,
                dir.resolve("inquisitor").toString());
        System.setProperty(EvaluationReportSessionListener.HEADER_PROPERTY, "test run");

        val listener = new EvaluationReportSessionListener();
        listener.launcherSessionOpened(null);
        listener.launcherSessionClosed(null);

        assertThat(dir.resolve("inquisitor/evaluation.html")).exists();
        assertThat(dir.resolve("inquisitor/groups/explicit.html")).exists();
        assertThat(dir.resolve("inquisitor/evaluation.json")).doesNotExist();
        assertThat(dir.resolve("inquisitor/evaluation.md")).doesNotExist();
        assertThat(EvaluationReportSession.records()).isEmpty();
    }

    @Test
    void honoursTheRequestedFormats() {
        EvaluationReportSession.register(recorderWithOneRecord(),
                new EvaluationRunInfo(null, null, null, null));
        System.setProperty(EvaluationReportSessionListener.REPORT_DIR_PROPERTY,
                dir.resolve("inquisitor").toString());
        System.setProperty(EvaluationReportSessionListener.FORMATS_PROPERTY, "markdown, json");

        new EvaluationReportSessionListener().launcherSessionClosed(null);

        assertThat(dir.resolve("inquisitor/evaluation.md")).exists();
        assertThat(dir.resolve("inquisitor/evaluation.json")).exists();
        assertThat(dir.resolve("inquisitor/evaluation.html")).doesNotExist();
    }

    @Test
    void doesNothingWithoutTheReportDirProperty() throws Exception {
        EvaluationReportSession.register(recorderWithOneRecord(),
                new EvaluationRunInfo(null, null, null, null));

        new EvaluationReportSessionListener().launcherSessionClosed(null);

        try (val files = Files.list(dir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void neverFailsTheSessionCloseWhenRenderingThrowsAnError() {
        EvaluationReportSession.register(recorderWithOneRecord(),
                new EvaluationRunInfo(null, null, null, null));
        System.setProperty(EvaluationReportSessionListener.REPORT_DIR_PROPERTY,
                dir.resolve("inquisitor").toString());
        System.setProperty(EvaluationReportSessionListener.FORMATS_PROPERTY, "exploding");

        val listener = new EvaluationReportSessionListener();
        listener.launcherSessionOpened(null);
        assertThatNoException().isThrownBy(() -> listener.launcherSessionClosed(null));

        assertThat(EvaluationReportSession.records()).isEmpty();
    }

    @Test
    void doesNothingWithoutRecords() {
        System.setProperty(EvaluationReportSessionListener.REPORT_DIR_PROPERTY,
                dir.resolve("inquisitor").toString());

        new EvaluationReportSessionListener().launcherSessionClosed(null);

        assertThat(dir.resolve("inquisitor")).doesNotExist();
    }
}
