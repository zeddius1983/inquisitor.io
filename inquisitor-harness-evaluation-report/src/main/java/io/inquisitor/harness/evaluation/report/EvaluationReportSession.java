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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import io.inquisitor.harness.evaluation.StepEvaluationRecord;
import io.inquisitor.harness.evaluation.StepEvaluationRecorder;
import org.jspecify.annotations.Nullable;

/**
 * The JVM-wide bridge between the recorder beans (living in one or more cached Spring
 * test contexts) and the JUnit {@code LauncherSessionListener} that writes the report
 * after the whole test plan: the starter registers each recorder here; the listener
 * drains them once at session close. Deliberately the only static state in the module —
 * a launcher session spans all contexts of a test JVM, so the registry's lifetime
 * matches the report's scope.
 */
public final class EvaluationReportSession {

    private static final List<StepEvaluationRecorder> RECORDERS = new CopyOnWriteArrayList<>();
    private static final AtomicReference<@Nullable EvaluationRunInfo> RUN_INFO = new AtomicReference<>();

    private EvaluationReportSession() {
    }

    /** Registers a recorder (idempotent) and the run info, first registration wins. */
    public static void register(StepEvaluationRecorder recorder, EvaluationRunInfo runInfo) {
        if (!RECORDERS.contains(recorder)) {
            RECORDERS.add(recorder);
        }
        RUN_INFO.compareAndSet(null, runInfo);
    }

    /** All records of all registered recorders, in registration + execution order. */
    public static List<StepEvaluationRecord> records() {
        return RECORDERS.stream().flatMap(recorder -> recorder.records().stream()).toList();
    }

    public static @Nullable EvaluationRunInfo runInfo() {
        return RUN_INFO.get();
    }

    /** Forgets all registrations — the session listener calls this after writing. */
    public static void reset() {
        RECORDERS.clear();
        RUN_INFO.set(null);
    }
}
