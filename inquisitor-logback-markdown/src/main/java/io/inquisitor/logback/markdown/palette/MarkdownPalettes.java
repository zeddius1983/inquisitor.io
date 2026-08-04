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

package io.inquisitor.logback.markdown.palette;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

/** Resolves built-in and service-provided Markdown palettes by name. */
public final class MarkdownPalettes {

    /** Name of the default built-in palette. */
    public static final String DEFAULT_NAME = "gruvbox";

    private static final List<MarkdownPaletteProvider> BUILT_INS =
            BuiltinMarkdownPaletteProvider.providers();
    private static final MarkdownPalette DEFAULT = findProvider(BUILT_INS, DEFAULT_NAME)
            .map(MarkdownPaletteProvider::palette)
            .orElseThrow();

    private MarkdownPalettes() {
    }

    /** Returns the default built-in Gruvbox palette. */
    public static MarkdownPalette defaultPalette() {
        return DEFAULT;
    }

    /**
     * Finds a palette with the thread context class loader.
     *
     * @param name built-in or provider name
     * @return matching palette, if one is available
     */
    public static Optional<MarkdownPalette> find(String name) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader classLoader = contextClassLoader == null
                ? MarkdownPalettes.class.getClassLoader()
                : contextClassLoader;
        return find(name, classLoader);
    }

    /**
     * Resolves a palette or throws a descriptive exception.
     *
     * @param name built-in or provider name
     * @return matching palette
     */
    public static MarkdownPalette require(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException(
                "Unknown Markdown palette '" + name + "'"));
    }

    static Optional<MarkdownPalette> find(String name, ClassLoader classLoader) {
        String normalized = normalize(name);
        Optional<MarkdownPalette> builtIn = findProvider(BUILT_INS, normalized)
                .map(MarkdownPaletteProvider::palette);
        if (builtIn.isPresent()) {
            return builtIn;
        }

        List<MarkdownPaletteProvider> matches = ServiceLoader
                .load(MarkdownPaletteProvider.class, classLoader)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(provider -> matches(provider, normalized))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Multiple Markdown palette providers use the name '" + name + "'");
        }
        return matches.stream()
                .findFirst()
                .map(MarkdownPaletteProvider::palette)
                .map(MarkdownPalettes::validate);
    }

    private static Optional<MarkdownPaletteProvider> findProvider(
            List<MarkdownPaletteProvider> providers,
            String normalizedName) {
        return providers.stream()
                .filter(provider -> matches(provider, normalizedName))
                .findFirst();
    }

    private static boolean matches(
            MarkdownPaletteProvider provider,
            String normalizedName) {
        return normalize(provider.name()).equals(normalizedName);
    }

    /** Validates and returns a palette supplied through a programmatic seam. */
    public static MarkdownPalette validate(MarkdownPalette palette) {
        MarkdownPalette candidate = Objects.requireNonNull(palette, "palette");
        validate("foreground", candidate.foreground());
        validate("surface", candidate.surface());
        validate("muted", candidate.muted());
        validate("blue", candidate.blue());
        validate("aqua", candidate.aqua());
        validate("green", candidate.green());
        validate("orange", candidate.orange());
        validate("purple", candidate.purple());
        validate("red", candidate.red());
        validate("yellow", candidate.yellow());
        return candidate;
    }

    private static void validate(String color, int value) {
        if (value < 0 || value > 0xffffff) {
            throw new IllegalArgumentException(
                    color + " must be an RGB value between 0x000000 and 0xffffff");
        }
    }

    private static String normalize(String name) {
        String normalized = Objects.requireNonNull(name, "name")
                .strip()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Markdown palette name must not be blank");
        }
        return normalized;
    }
}
