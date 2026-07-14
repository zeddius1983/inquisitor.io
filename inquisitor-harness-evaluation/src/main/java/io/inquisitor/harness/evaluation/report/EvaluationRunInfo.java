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

import org.jspecify.annotations.Nullable;

/**
 * The model configuration an evaluation ran against — captured from the Spring
 * {@code Environment} by the starter (the session listener has no context to ask).
 */
public record EvaluationRunInfo(
        @Nullable String actorModel,
        @Nullable String actorBaseUrl,
        @Nullable String judgeModel,
        @Nullable String judgeBaseUrl) {
}
