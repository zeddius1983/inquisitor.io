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

package io.inquisitor.logback.markdown.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for automatic Markdown console rendering.
 *
 * @param enabled whether compatible Logback console layouts should be enhanced
 * @param consoleMode whether the starter preserves shared console output or emits
 *                    only marker-selected Markdown events
 * @param palette built-in or service-provided terminal palette name
 */
@ConfigurationProperties("inquisitor.logging.markdown")
public record MarkdownLoggingProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("auto") MarkdownConsoleMode consoleMode,
        @DefaultValue("gruvbox") String palette) {
}
