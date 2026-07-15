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

package io.inquisitor.harness.gradle;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.testing.Test;

/**
 * The {@code evaluate} task: a {@link Test} run of the scenario tests with the LLM
 * gate + LLM-as-judge evaluation on, plus the {@code --report} option selecting which
 * report formats the test JVM writes (renderer names, comma-separated; default
 * {@code html}). User-supplied renderers on the test classpath are selectable by
 * their {@code EvaluationReportRenderer.name()} the same way as the built-ins.
 */
public abstract class EvaluateTask extends Test {

    /** Comma-separated report format names, e.g. {@code html,markdown,json}. */
    @Input
    public abstract Property<String> getReportFormats();

    @Option(option = "report", description = "Report format(s) to write, comma-separated "
            + "renderer names (built-ins: html, markdown, json). Default: html.")
    public void setReport(String formats) {
        getReportFormats().set(formats);
    }
}
