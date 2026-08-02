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

import io.inquisitor.logback.markdown.palette.MarkdownPalette;
import io.inquisitor.logback.markdown.palette.MarkdownPalettes;
import org.jline.jansi.Ansi;

/** Applies palette-aware ANSI styles used by renderers and Logback converters. */
public final class AnsiStyler {

    static final double MINIMUM_TEXT_CONTRAST = 4.5;

    private static final int BLACK = 0x000000;
    private static final int WHITE = 0xffffff;

    private final boolean enabled;
    private final MarkdownPalette palette;

    AnsiStyler(boolean enabled) {
        this(enabled, MarkdownPalettes.defaultPalette());
    }

    public AnsiStyler(boolean enabled, MarkdownPalette palette) {
        this.enabled = enabled;
        this.palette = MarkdownPalettes.validate(palette);
    }

    public String open(TextStyle style) {
        if (!enabled) {
            return "";
        }
        Ansi ansi = new Ansi();
        apply(style, ansi);
        return ansi.toString();
    }

    public String reset() {
        return enabled ? new Ansi().reset().toString() : "";
    }

    public String open(PowerlineStyle style) {
        if (!enabled) {
            return "";
        }
        return new Ansi()
                .bgRgb(background(style))
                .fgRgb(foreground(style))
                .bold()
                .toString();
    }

    public String powerlineCap(PowerlineStyle style, String glyph) {
        if (!enabled) {
            return glyph;
        }
        return new Ansi().reset()
                .fgRgb(background(style))
                .a(glyph)
                .reset()
                .toString();
    }

    public String powerlineTransition(
            PowerlineStyle current,
            PowerlineStyle next,
            String glyph) {
        if (!enabled) {
            return glyph;
        }
        return new Ansi()
                .fgRgb(background(current))
                .bgRgb(background(next))
                .a(glyph)
                .reset()
                .toString();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public enum PowerlineStyle {
        TIMESTAMP,
        SCENARIO,
        STEP,
        PROGRESS,
        STATUS,
        DURATION,
        FAILURE,
        WARNING,
        DEBUG,
        TRACE;

        private static final PowerlineStyle[] PALETTE = {
                SCENARIO, STEP, PROGRESS, STATUS, DURATION
        };

        public static PowerlineStyle at(int index) {
            return PALETTE[index % PALETTE.length];
        }

    }

    public enum TextStyle {
        HEADING,
        STRONG,
        EMPHASIS,
        INLINE_CODE,
        CODE_BLOCK,
        CODE_KEYWORD,
        CODE_STRING,
        CODE_NUMBER,
        CODE_COMMENT,
        CODE_PROPERTY,
        CODE_VARIABLE,
        CODE_OPERATOR,
        TABLE_BORDER,
        TABLE_HEADER,
        LIST_MARKER,
        QUOTE_MARKER,
        LINK
    }

    int background(PowerlineStyle style) {
        return switch (style) {
            case TIMESTAMP -> palette.surface();
            case SCENARIO -> palette.orange();
            case STEP, WARNING -> palette.yellow();
            case PROGRESS -> palette.aqua();
            case STATUS -> palette.green();
            case DURATION, TRACE -> palette.muted();
            case FAILURE -> palette.red();
            case DEBUG -> palette.purple();
        };
    }

    int foreground(PowerlineStyle style) {
        int background = background(style);
        int light = palette.foreground();
        int dark = palette.surface();
        int preferred = contrast(light, background) >= contrast(dark, background)
                ? light
                : dark;
        if (contrast(preferred, background) >= MINIMUM_TEXT_CONTRAST) {
            return preferred;
        }
        return contrast(BLACK, background) >= contrast(WHITE, background)
                ? BLACK
                : WHITE;
    }

    static double contrast(int first, int second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double luminance(int rgb) {
        return 0.2126 * linearChannel(rgb >> 16)
                + 0.7152 * linearChannel(rgb >> 8)
                + 0.0722 * linearChannel(rgb);
    }

    private static double linearChannel(int rgb) {
        double channel = (rgb & 0xff) / 255.0;
        return channel <= 0.04045
                ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private void apply(TextStyle style, Ansi ansi) {
        switch (style) {
            case HEADING -> ansi.fgRgb(palette.aqua()).bold();
            case STRONG -> ansi.fgRgb(palette.yellow()).bold();
            case EMPHASIS -> ansi.fgRgb(palette.foreground()).a(Ansi.Attribute.ITALIC);
            case INLINE_CODE -> ansi.fgRgb(palette.purple());
            case CODE_BLOCK -> ansi.bgRgb(palette.surface()).fgRgb(palette.foreground());
            case CODE_KEYWORD -> ansi.bgRgb(palette.surface()).fgRgb(palette.purple()).bold();
            case CODE_STRING -> ansi.bgRgb(palette.surface()).fgRgb(palette.green());
            case CODE_NUMBER -> ansi.bgRgb(palette.surface()).fgRgb(palette.aqua());
            case CODE_COMMENT -> ansi.bgRgb(palette.surface()).fgRgb(palette.muted())
                    .a(Ansi.Attribute.ITALIC);
            case CODE_PROPERTY -> ansi.bgRgb(palette.surface()).fgRgb(palette.blue());
            case CODE_VARIABLE -> ansi.bgRgb(palette.surface()).fgRgb(palette.yellow());
            case CODE_OPERATOR -> ansi.bgRgb(palette.surface()).fgRgb(palette.foreground());
            case TABLE_BORDER -> ansi.fgRgb(palette.muted())
                    .a(Ansi.Attribute.INTENSITY_FAINT);
            case TABLE_HEADER -> ansi.fgRgb(palette.aqua()).bold();
            case LIST_MARKER -> ansi.fgRgb(palette.green()).bold();
            case QUOTE_MARKER -> ansi.fgRgb(palette.orange());
            case LINK -> ansi.fgRgb(palette.blue());
        }
    }
}
