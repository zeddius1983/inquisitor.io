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

import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPaletteProvider;
import io.inquisitor.logback.markdown.palette.RgbMarkdownPalette;

public final class StarterTestPaletteProvider implements MarkdownPaletteProvider {

    private static final MarkdownPalette PALETTE = new RgbMarkdownPalette(
            0xf0f0f0, 0x202020, 0x505050,
            0x2266aa, 0x12abef, 0x55aa55, 0xcc7722, 0xaa55aa, 0xcc4444, 0xddaa33);

    @Override
    public String name() {
        return "starter-test";
    }

    @Override
    public MarkdownPalette palette() {
        return PALETTE;
    }
}
