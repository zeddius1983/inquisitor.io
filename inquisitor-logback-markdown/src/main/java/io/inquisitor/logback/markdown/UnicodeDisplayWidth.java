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

package io.inquisitor.logback.markdown;

import java.text.BreakIterator;
import java.util.Locale;

/** Deterministic wcwidth-style measurement for common terminal text. */
final class UnicodeDisplayWidth implements DisplayWidth {

    static final UnicodeDisplayWidth INSTANCE = new UnicodeDisplayWidth();

    private static final int ESCAPE = 0x1B;
    private static final int KEYCAP = 0x20E3;
    private static final int ZERO_WIDTH_JOINER = 0x200D;

    private UnicodeDisplayWidth() { }

    @Override
    public int width(CharSequence text) {
        String visible = removeAnsi(text);
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(visible);
        int width = 0;
        int start = graphemes.first();
        for (int end = graphemes.next(); end != BreakIterator.DONE;
                start = end, end = graphemes.next()) {
            width += graphemeWidth(visible, start, end);
        }
        return width;
    }

    private static int graphemeWidth(String value, int start, int end) {
        int width = 0;
        for (int index = start; index < end; ) {
            int codePoint = value.codePointAt(index);
            if (codePoint == KEYCAP) {
                return 2;
            }
            width = Math.max(width, codePointWidth(codePoint));
            index += Character.charCount(codePoint);
        }
        return width;
    }

    private static int codePointWidth(int codePoint) {
        if (codePoint == ZERO_WIDTH_JOINER
                || isVariationSelector(codePoint)
                || isControl(codePoint)
                || isMark(codePoint)) {
            return 0;
        }
        return isWide(codePoint) ? 2 : 1;
    }

    private static boolean isMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT;
    }

    private static boolean isControl(int codePoint) {
        return codePoint == 0
                || codePoint < 0x20
                || (codePoint >= 0x7F && codePoint < 0xA0);
    }

    private static boolean isVariationSelector(int codePoint) {
        return (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || (codePoint >= 0xE0100 && codePoint <= 0xE01EF);
    }

    private static boolean isWide(int codePoint) {
        return codePoint >= 0x1100 && (
                codePoint <= 0x115F
                || codePoint == 0x2329
                || codePoint == 0x232A
                || (codePoint >= 0x2E80 && codePoint <= 0xA4CF && codePoint != 0x303F)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE10 && codePoint <= 0xFE19)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF)
                || (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD)
                || (codePoint >= 0x2600 && codePoint <= 0x27BF));
    }

    private static String removeAnsi(CharSequence text) {
        StringBuilder visible = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); ) {
            int codePoint = Character.codePointAt(text, index);
            if (codePoint != ESCAPE) {
                visible.appendCodePoint(codePoint);
                index += Character.charCount(codePoint);
                continue;
            }
            index = skipEscapeSequence(text, index);
        }
        return visible.toString();
    }

    private static int skipEscapeSequence(CharSequence text, int escapeIndex) {
        int index = escapeIndex + 1;
        if (index >= text.length()) {
            return index;
        }
        char introducer = text.charAt(index++);
        if (introducer == '[') {
            while (index < text.length()) {
                char candidate = text.charAt(index++);
                if (candidate >= '@' && candidate <= '~') {
                    break;
                }
            }
            return index;
        }
        if (introducer == ']') {
            while (index < text.length()) {
                char candidate = text.charAt(index++);
                if (candidate == '\u0007') {
                    break;
                }
                if (candidate == ESCAPE && index < text.length() && text.charAt(index) == '\\') {
                    index++;
                    break;
                }
            }
        }
        return index;
    }
}
