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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarkdownPalettesTest {

    @ParameterizedTest
    @CsvSource({
            "gruvbox, GRUVBOX",
            "nord, NORD",
            "catppuccin, CATPPUCCIN",
            "tokyo-night, TOKYO_NIGHT"
    })
    void resolvesRgbBackedBuiltInsWithRelaxedNames(String name, String relaxedName) {
        MarkdownPalette palette = MarkdownPalettes.require(name);

        assertSame(palette, MarkdownPalettes.require(relaxedName));
        assertInstanceOf(RgbMarkdownPalette.class, palette);
    }

    @Test
    void defaultPaletteIsTheNamedGruvboxRgbPalette() {
        assertSame(MarkdownPalettes.require(MarkdownPalettes.DEFAULT_NAME),
                MarkdownPalettes.defaultPalette());
        assertInstanceOf(RgbMarkdownPalette.class, MarkdownPalettes.defaultPalette());
        assertSame(MarkdownPalettes.defaultPalette(), MarkdownPalettes.require("gruvbox"));
        assertNotSame(
                ConflictingGruvboxTestPaletteProvider.PALETTE,
                MarkdownPalettes.require("gruvbox"));
    }

    @Test
    void resolvesAServiceProvidedPalette() {
        assertSame(SolarizedTestPaletteProvider.PALETTE,
                MarkdownPalettes.require("solarized-test"));
    }

    @Test
    void reportsAnUnknownPalette() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MarkdownPalettes.require("missing"));

        assertEquals("Unknown Markdown palette 'missing'", exception.getMessage());
    }

    @Test
    void validatesRgbPaletteValues() {
        assertThrows(IllegalArgumentException.class, () -> new RgbMarkdownPalette(
                -1, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RgbMarkdownPalette(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0x1000000));
    }
}
