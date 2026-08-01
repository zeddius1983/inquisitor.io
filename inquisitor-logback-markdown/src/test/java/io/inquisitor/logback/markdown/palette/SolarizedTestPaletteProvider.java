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

public final class SolarizedTestPaletteProvider implements MarkdownPaletteProvider {

    static final MarkdownPalette PALETTE = new RgbMarkdownPalette(
            0xeee8d5, 0x002b36, 0x586e75,
            0x268bd2, 0x2aa198, 0x859900, 0xcb4b16, 0x6c71c4, 0xdc322f, 0xb58900);

    @Override
    public String name() {
        return "solarized-test";
    }

    @Override
    public MarkdownPalette palette() {
        return PALETTE;
    }
}
