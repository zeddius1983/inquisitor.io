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

package io.inquisitor.logback.markdown.converter;

import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPalettes;

/** Shared parsing for Logback Markdown converter options. */
final class ConverterOptions {

    private static final String PALETTE = "palette";

    private ConverterOptions() {
    }

    static boolean hasFlag(List<String> options, String expected) {
        return options.stream().map(String::strip).anyMatch(expected::equalsIgnoreCase);
    }

    static List<String> named(List<String> options, String expected) {
        return options.stream()
                .map(String::strip)
                .filter(option -> isNamed(option, expected))
                .toList();
    }

    static int intValue(
            List<String> options,
            String name,
            String conversionWord,
            int fallback,
            IntPredicate valid,
            Consumer<String> warning) {
        List<String> configured = named(options, name);
        if (configured.isEmpty()) {
            return fallback;
        }
        if (configured.size() > 1) {
            warning.accept("Multiple " + name + " options were configured for "
                    + conversionWord + "; using the last one");
        }
        String option = configured.getLast();
        int separator = option.indexOf('=');
        if (separator >= 0) {
            String value = option.substring(separator + 1).strip();
            try {
                int parsed = Integer.parseInt(value);
                if (!value.isEmpty() && valid.test(parsed)) {
                    return parsed;
                }
            }
            catch (NumberFormatException ignored) {
                // Report every malformed value consistently below.
            }
        }
        warning.accept("Invalid " + conversionWord + " option '" + option
                + "'; using the default " + name + "=" + fallback);
        return fallback;
    }

    static MarkdownPalette palette(
            List<String> options,
            String conversionWord,
            Consumer<String> warning,
            BiConsumer<String, Throwable> warningWithCause) {
        List<String> configured = named(options, PALETTE);
        if (configured.isEmpty()) {
            return MarkdownPalettes.defaultPalette();
        }
        if (configured.size() > 1) {
            warning.accept("Multiple palette options were configured for "
                    + conversionWord + "; using the last one");
        }
        String option = configured.getLast();
        int separator = option.indexOf('=');
        if (separator < 0 || option.substring(separator + 1).strip().isEmpty()) {
            return invalidPalette(conversionWord, option, warning);
        }
        String name = option.substring(separator + 1).strip();
        try {
            return MarkdownPalettes.find(name).orElseGet(() ->
                    invalidPalette(conversionWord, option, warning));
        }
        catch (RuntimeException | ServiceConfigurationError exception) {
            warningWithCause.accept("Could not resolve " + conversionWord + " option '"
                    + option + "'; using palette=gruvbox", exception);
            return MarkdownPalettes.defaultPalette();
        }
    }

    private static MarkdownPalette invalidPalette(
            String conversionWord,
            String option,
            Consumer<String> warning) {
        warning.accept("Invalid " + conversionWord + " option '" + option
                + "'; using palette=gruvbox");
        return MarkdownPalettes.defaultPalette();
    }

    private static boolean isNamed(String option, String expected) {
        int separator = option.indexOf('=');
        String name = separator < 0 ? option : option.substring(0, separator).strip();
        return expected.equalsIgnoreCase(name);
    }
}
