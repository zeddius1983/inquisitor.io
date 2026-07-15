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

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Writes the evaluation report once, after the whole test plan — pass or fail —
 * inside the test JVM where the data lives. Registered via {@code ServiceLoader}
 * ({@code META-INF/services}), so it is active whenever this module is on the test
 * classpath; it does nothing unless {@value #REPORT_DIR_PROPERTY} is set (the Gradle
 * plugin's {@code evaluate} task sets it) and at least one step was recorded.
 */
@Slf4j
public class EvaluationReportSessionListener implements LauncherSessionListener {

    /** Target directory for the report artifacts; absent on ordinary test runs. */
    public static final String REPORT_DIR_PROPERTY = "inquisitor.report.dir";

    /** Free-form run label rendered into the report (the plugin's {@code -Pheader}). */
    public static final String HEADER_PROPERTY = "inquisitor.report.header";

    /**
     * Comma-separated renderer names to write (the plugin's {@code --report} option);
     * defaults to {@value #DEFAULT_FORMATS}.
     */
    public static final String FORMATS_PROPERTY = "inquisitor.report.formats";

    /** The default report format. */
    public static final String DEFAULT_FORMATS = "html";

    private volatile Instant openedAt = Instant.now();

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        openedAt = Instant.now();
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        try {
            val dir = System.getProperty(REPORT_DIR_PROPERTY);
            if (dir == null || dir.isBlank()) {
                return;
            }
            val records = EvaluationReportSession.records();
            if (records.isEmpty()) {
                return;
            }
            val generatedAt = Instant.now();
            val report = EvaluationReport.of(generatedAt, Duration.between(openedAt, generatedAt),
                    System.getProperty(HEADER_PROPERTY), EvaluationReportSession.runInfo(), records);
            val files = EvaluationReportWriter.discover(requestedFormats()).write(report, Path.of(dir));
            // Println on purpose: this must reach the console even without a logger config.
            // Entry pages only — the multi-page HTML report has one file per scenario.
            val reportDir = Path.of(dir);
            System.out.println("Inquisitor evaluation report written: "
                    + files.stream().filter(file -> reportDir.equals(file.getParent())).toList());
        } catch (RuntimeException e) {
            // Never let report writing fail the JVM's orderly test shutdown.
            log.warn("Could not write the evaluation report", e);
        } finally {
            EvaluationReportSession.reset();
        }
    }

    private static Set<String> requestedFormats() {
        val formats = System.getProperty(FORMATS_PROPERTY, DEFAULT_FORMATS);
        val requested = Arrays.stream(formats.split(","))
                .map(String::trim)
                .filter(format -> !format.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return requested.isEmpty() ? Set.of(DEFAULT_FORMATS) : requested;
    }
}
