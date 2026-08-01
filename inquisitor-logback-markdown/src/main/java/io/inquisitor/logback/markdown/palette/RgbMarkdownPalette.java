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

/** Immutable {@link MarkdownPalette} backed by ten {@code 0xRRGGBB} values. */
public record RgbMarkdownPalette(
        int foreground,
        int surface,
        int muted,
        int blue,
        int aqua,
        int green,
        int orange,
        int purple,
        int red,
        int yellow) implements MarkdownPalette {

    /** Validates every RGB value when the palette is created. */
    public RgbMarkdownPalette {
        validate("foreground", foreground);
        validate("surface", surface);
        validate("muted", muted);
        validate("blue", blue);
        validate("aqua", aqua);
        validate("green", green);
        validate("orange", orange);
        validate("purple", purple);
        validate("red", red);
        validate("yellow", yellow);
    }

    private static void validate(String name, int value) {
        if (value < 0 || value > 0xffffff) {
            throw new IllegalArgumentException(
                    name + " must be an RGB value between 0x000000 and 0xffffff");
        }
    }
}
