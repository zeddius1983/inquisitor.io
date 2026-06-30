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
 * Step evaluation: a second, independent LLM judges whether a step's verdict is
 * <em>earned</em> by auditing the actor's claim against the real tool-call trace, rather
 * than trusting the actor's own verdict, producing a per-step credibility score. See
 * {@code tasks/task-08-evaluation.md}.
 */
@NullMarked
package io.inquisitor.harness.evaluation;

import org.jspecify.annotations.NullMarked;
