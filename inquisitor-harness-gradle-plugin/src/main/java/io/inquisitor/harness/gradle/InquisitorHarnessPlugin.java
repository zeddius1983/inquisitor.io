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

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Set;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.testing.Test;

/**
 * Registers the {@code evaluate} task: a {@link Test} task over the {@code test} source
 * set that selects the scenario tests by JUnit tag and switches on the LLM gate
 * ({@code INQUISITOR_LLM_IT}) plus LLM-as-judge evaluation ({@code INQUISITOR_EVAL}).
 * Deliberately not wired into {@code check} — evaluation is an explicit opt-in run.
 */
public class InquisitorHarnessPlugin implements Plugin<Project> {

    /** The task group all harness tasks live under. */
    public static final String HARNESS_GROUP = "harness";

    /** The name of the evaluation task this plugin registers. */
    public static final String EVALUATE_TASK_NAME = "evaluate";

    /** System property carrying the report header label (rendered into the report). */
    public static final String REPORT_HEADER_PROPERTY = "inquisitor.report.header";

    /** The name of the report-echoing finalizer task. */
    public static final String EVALUATE_REPORT_TASK_NAME = "evaluateReport";

    /** System property telling the test JVM where to write the report artifacts. */
    public static final String REPORT_DIR_PROPERTY = "inquisitor.report.dir";

    @Override
    public void apply(Project project) {
        var extension = project.getExtensions().create("harness", InquisitorHarnessExtension.class);
        extension.getTags().convention(Set.of("inquisitor"));

        project.getPlugins().withType(JavaPlugin.class,
                javaPlugin -> registerEvaluateTasks(project, extension));
    }

    private void registerEvaluateTasks(Project project, InquisitorHarnessExtension extension) {
        var testSourceSet = project.getExtensions().getByType(SourceSetContainer.class)
                .getByName(SourceSet.TEST_SOURCE_SET_NAME);
        var header = project.getProviders().gradleProperty("header");
        var reportDir = project.getLayout().getBuildDirectory().dir("reports/inquisitor");

        var reportTask = project.getTasks().register(EVALUATE_REPORT_TASK_NAME,
                EvaluationReportTask.class, task -> {
                    task.setGroup(HARNESS_GROUP);
                    task.setDescription("Prints the evaluation report written by the evaluate task.");
                    task.getReportDir().convention(reportDir);
                    // The report only exists when the evaluation modules ran in the test JVM.
                    task.onlyIf("an evaluation report was written",
                            t -> Files.exists(((EvaluationReportTask) t).getMarkdownReport()));
                });

        project.getTasks().register(EVALUATE_TASK_NAME, Test.class, task -> {
            task.setGroup(HARNESS_GROUP);
            task.setDescription("Runs Inquisitor scenario tests with LLM-as-judge evaluation.");
            task.setTestClassesDirs(testSourceSet.getOutput().getClassesDirs());
            task.setClasspath(testSourceSet.getRuntimeClasspath());
            task.useJUnitPlatform(options ->
                    options.includeTags(extension.getTags().get().toArray(String[]::new)));
            task.environment("INQUISITOR_LLM_IT", "true");
            task.environment("INQUISITOR_EVAL", "true");
            task.getJvmArgumentProviders().add(() -> {
                var arguments = new ArrayList<String>();
                arguments.add("-D" + REPORT_DIR_PROPERTY + "="
                        + reportDir.get().getAsFile().getAbsolutePath());
                if (header.isPresent()) {
                    arguments.add("-D" + REPORT_HEADER_PROPERTY + "=" + header.get());
                }
                return arguments;
            });
            // The report is echoed even — especially — when scenario tests fail.
            task.finalizedBy(reportTask);
        });
    }
}
