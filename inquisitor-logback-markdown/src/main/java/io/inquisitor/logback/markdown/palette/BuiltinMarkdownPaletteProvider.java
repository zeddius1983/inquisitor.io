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

/** One named, RGB-backed palette shipped with the renderer. */
record BuiltinMarkdownPaletteProvider(
        String name,
        MarkdownPalette palette) implements MarkdownPaletteProvider {

    /** Returns the deterministic provider catalog that does not rely on service metadata. */
    static List<MarkdownPaletteProvider> providers() {
        return List.of(
                new BuiltinMarkdownPaletteProvider("gruvbox", new RgbMarkdownPalette(
                        0xfbf1c7, 0x3c3836, 0x665c54,
                        0x458588, 0x689d6a, 0x98971a, 0xd65d0e, 0xb16286,
                        0xcc241d, 0xd79921)),
                new BuiltinMarkdownPaletteProvider("nord", new RgbMarkdownPalette(
                        0xeceff4, 0x3b4252, 0x4c566a,
                        0x5e81ac, 0x8fbcbb, 0xa3be8c, 0xd08770, 0xb48ead,
                        0xbf616a, 0xebcb8b)),
                new BuiltinMarkdownPaletteProvider("catppuccin", new RgbMarkdownPalette(
                        0xcdd6f4, 0x313244, 0x6c7086,
                        0x89b4fa, 0x94e2d5, 0xa6e3a1, 0xfab387, 0xcba6f7,
                        0xf38ba8, 0xf9e2af)),
                new BuiltinMarkdownPaletteProvider("tokyo-night", new RgbMarkdownPalette(
                        0xc0caf5, 0x24283b, 0x565f89,
                        0x7aa2f7, 0x7dcfff, 0x9ece6a, 0xff9e64, 0xbb9af7,
                        0xf7768e, 0xe0af68)));
    }
}
