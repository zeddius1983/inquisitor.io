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

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The artifact of record: the full report model as pretty-printed JSON, the input for
 * any downstream generation (the future README verified-models table, trend tracking).
 */
public class JsonReportRenderer implements EvaluationReportRenderer {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    @Override
    public String fileName() {
        return "evaluation.json";
    }

    @Override
    public String render(EvaluationReport report) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
    }
}
