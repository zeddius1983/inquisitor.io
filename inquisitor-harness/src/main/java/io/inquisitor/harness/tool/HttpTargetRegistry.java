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

package io.inquisitor.harness.tool;

/**
 * Allow-list of {@link HttpTarget}s the {@link HttpRequestTool} may reach. A call
 * may name a target; if it omits the name, the sole registered target is used.
 * Resolving an unregistered name fails, so the LLM can only call configured targets.
 */
public class HttpTargetRegistry extends NamedRegistry<HttpTarget> {

    public HttpTargetRegistry() {
        super("HTTP target");
    }
}
