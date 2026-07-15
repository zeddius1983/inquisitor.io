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

/**
 * The evaluation report: aggregates every {@code StepEvaluationRecord} of the test JVM
 * into a bucket-grouped document and writes it as JSON (artifact of record) + Markdown
 * at the end of the JUnit launcher session. Written only when
 * {@code inquisitor.report.dir} is set — the Gradle plugin's {@code evaluate} task does
 * that; plain test runs write nothing. See {@code tasks/task-12-evaluation-report.md}.
 */
@NullMarked
package io.inquisitor.harness.evaluation.report;

import org.jspecify.annotations.NullMarked;
