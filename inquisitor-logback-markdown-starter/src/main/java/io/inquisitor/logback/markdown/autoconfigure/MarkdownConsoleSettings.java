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

import java.util.ServiceConfigurationError;

import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPalettes;
import io.inquisitor.logback.markdown.renderer.FlexmarkAnsiRenderer;
import io.inquisitor.logback.markdown.renderer.MarkdownRenderer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

@Slf4j
final class MarkdownConsoleSettings {

    private static final String ENABLED = "inquisitor.logging.markdown.enabled";
    private static final String CONSOLE_MODE = "inquisitor.logging.markdown.console-mode";
    private static final String PALETTE = "inquisitor.logging.markdown.palette";
    private static final String HARNESS_FORMAT = "inquisitor.harness.logging.format";

    private MarkdownConsoleSettings() {
    }

    static boolean enabled(Environment environment) {
        return environment.getProperty(ENABLED, Boolean.class, true);
    }

    static MarkdownConsoleMode consoleMode(Environment environment) {
        val configured = Binder.get(environment)
                .bind(CONSOLE_MODE, MarkdownConsoleMode.class)
                .orElse(MarkdownConsoleMode.AUTO);
        return consoleMode(configured, environment);
    }

    static MarkdownConsoleMode consoleMode(
            MarkdownConsoleMode configured,
            Environment environment) {
        if (configured != MarkdownConsoleMode.AUTO) {
            return configured;
        }
        val harnessFormat = environment.getProperty(HARNESS_FORMAT);
        return harnessFormat != null && harnessFormat.equalsIgnoreCase("markdown")
                ? MarkdownConsoleMode.EXCLUSIVE
                : MarkdownConsoleMode.SHARED;
    }

    static MarkdownPalette palette(Environment environment) {
        return paletteOrDefault(environment.getProperty(PALETTE, MarkdownPalettes.DEFAULT_NAME));
    }

    static MarkdownPalette paletteOrDefault(String name) {
        try {
            return MarkdownPalettes.find(name).orElseGet(() -> fallbackPalette(name));
        }
        catch (RuntimeException | ServiceConfigurationError exception) {
            log.warn("Could not resolve Markdown palette '{}'; using {}",
                    name, MarkdownPalettes.DEFAULT_NAME, exception);
            return MarkdownPalettes.defaultPalette();
        }
    }

    private static MarkdownPalette fallbackPalette(String name) {
        log.warn("Unknown Markdown palette '{}'; using {}", name, MarkdownPalettes.DEFAULT_NAME);
        return MarkdownPalettes.defaultPalette();
    }

    static MarkdownRenderer renderer(MarkdownPalette palette) {
        return switch (AnsiOutput.getEnabled()) {
            case ALWAYS -> new FlexmarkAnsiRenderer(true, palette);
            case NEVER -> new FlexmarkAnsiRenderer(false, palette);
            case DETECT -> new FlexmarkAnsiRenderer(palette);
        };
    }

    static @Nullable Boolean ansiPolicy() {
        return switch (AnsiOutput.getEnabled()) {
            case ALWAYS -> true;
            case NEVER -> false;
            case DETECT -> null;
        };
    }
}
