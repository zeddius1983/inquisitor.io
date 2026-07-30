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
import java.util.List;
import java.util.ServiceConfigurationError;

/**
 * A test-only renderer that throws an {@link Error} subtype when rendering — the
 * failure class that must never escape the session listener and kill the test JVM.
 */
public class ExplodingReportRenderer implements EvaluationReportRenderer {

    @Override
    public String name() {
        return "exploding";
    }

    @Override
    public List<Path> render(EvaluationReport report, Path dir) {
        throw new ServiceConfigurationError("boom");
    }
}
