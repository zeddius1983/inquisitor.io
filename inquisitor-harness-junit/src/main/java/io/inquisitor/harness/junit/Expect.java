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

/**
 * The expected outcome of a {@link Scenario @Scenario}.
 *
 * <ul>
 *   <li>{@link #PASS} (the default) — every step must pass; the first failing step
 *       fails the test.</li>
 *   <li>{@link #FAIL} — the scenario is expected to fail at some step. A failing step
 *       is the success condition (the rest are skipped); if <em>every</em> step
 *       passes, the test fails because the expected failure never happened.</li>
 * </ul>
 *
 * <p>{@code FAIL} is for fault detection (oracle calibration): a correct scenario is
 * run against a deliberately defective build and the oracle is expected to catch it.
 * See {@code tasks/task-07-fault-detection.md}.
 */
public enum Expect {
    PASS,
    FAIL
}
