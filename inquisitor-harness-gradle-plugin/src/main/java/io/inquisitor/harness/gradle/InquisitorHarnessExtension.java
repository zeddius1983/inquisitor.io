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

import org.gradle.api.provider.SetProperty;

/**
 * The {@code harness} build-script DSL.
 *
 * <pre>{@code
 * harness {
 *     tags = setOf("inquisitor")   // JUnit tag(s) the evaluate task selects
 * }
 * }</pre>
 */
public abstract class InquisitorHarnessExtension {

    /** JUnit tag(s) selecting the scenario tests the {@code evaluate} task runs. */
    public abstract SetProperty<String> getTags();
}
