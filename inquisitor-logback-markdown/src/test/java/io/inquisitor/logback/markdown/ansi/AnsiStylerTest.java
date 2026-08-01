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

package io.inquisitor.logback.markdown.ansi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPalettes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnsiStylerTest {

    @ParameterizedTest
    @ValueSource(strings = {"gruvbox", "nord", "catppuccin", "tokyo-night"})
    void everyBuiltInBreadcrumbMeetsNormalTextContrast(String paletteName) {
        MarkdownPalette palette = MarkdownPalettes.require(paletteName);
        AnsiStyler styler = new AnsiStyler(true, palette);

        assertTrue(List.of(AnsiStyler.PowerlineStyle.values()).stream()
                .allMatch(style -> AnsiStyler.contrast(
                        styler.foreground(style),
                        styler.background(style)) >= AnsiStyler.MINIMUM_TEXT_CONTRAST),
                () -> "Insufficient Powerline contrast for " + paletteName);
    }

    @ParameterizedTest
    @CsvSource({
            "GRUVBOX, SCENARIO",
            "GRUVBOX, PROGRESS",
            "GRUVBOX, STATUS",
            "GRUVBOX, DEBUG",
            "NORD, SCENARIO",
            "NORD, FAILURE",
            "NORD, DEBUG"
    })
    void gruvboxAndNordUseBlackWherePaletteNeutralsAreInsufficient(
            String paletteName,
            AnsiStyler.PowerlineStyle style) {
        MarkdownPalette palette = MarkdownPalettes.require(paletteName);
        AnsiStyler styler = new AnsiStyler(true, palette);

        assertEquals(0x000000, styler.foreground(style));
    }
}
