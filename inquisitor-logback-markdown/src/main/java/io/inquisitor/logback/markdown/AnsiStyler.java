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

import java.util.Locale;
import java.util.function.Consumer;

import org.jline.jansi.Ansi;
import org.jspecify.annotations.Nullable;

final class AnsiStyler {

    private final boolean enabled;

    AnsiStyler(boolean enabled) {
        this.enabled = enabled;
    }

    String open(TextStyle style) {
        if (!enabled) {
            return "";
        }
        Ansi ansi = new Ansi();
        style.apply(ansi);
        return ansi.toString();
    }

    String reset() {
        return enabled ? new Ansi().reset().toString() : "";
    }

    String open(PowerlineStyle style) {
        if (!enabled) {
            return "";
        }
        Ansi ansi = new Ansi().bg(style.background);
        if (style.brightForeground) {
            ansi.fgBright(style.foreground);
        }
        else {
            ansi.fg(style.foreground);
        }
        return ansi.bold().toString();
    }

    String powerlineCap(PowerlineStyle style, String glyph) {
        return enabled
                ? new Ansi().reset().fg(style.background).a(glyph).reset().toString()
                : glyph;
    }

    String powerlineTransition(
            PowerlineStyle current,
            PowerlineStyle next,
            String glyph) {
        return enabled
                ? new Ansi().fg(current.background).bg(next.background)
                        .a(glyph).reset().toString()
                : glyph;
    }

    boolean isEnabled() {
        return enabled;
    }

    enum PowerlineStyle {
        SCENARIO(Ansi.Color.BLUE, Ansi.Color.WHITE, true),
        STEP(Ansi.Color.CYAN, Ansi.Color.BLACK, false),
        PROGRESS(Ansi.Color.MAGENTA, Ansi.Color.WHITE, true),
        STATUS(Ansi.Color.GREEN, Ansi.Color.BLACK, false),
        DURATION(Ansi.Color.BLACK, Ansi.Color.WHITE, true),
        FAILURE(Ansi.Color.RED, Ansi.Color.WHITE, true),
        WARNING(Ansi.Color.YELLOW, Ansi.Color.BLACK, false);

        private static final PowerlineStyle[] PALETTE = {
                SCENARIO, STEP, PROGRESS, STATUS, DURATION
        };

        private final Ansi.Color background;
        private final Ansi.Color foreground;
        private final boolean brightForeground;

        PowerlineStyle(
                Ansi.Color background,
                Ansi.Color foreground,
                boolean brightForeground) {
            this.background = background;
            this.foreground = foreground;
            this.brightForeground = brightForeground;
        }

        static PowerlineStyle at(int index) {
            return PALETTE[index % PALETTE.length];
        }

        static PowerlineStyle forSegment(
                int index,
                String text,
                boolean httpBreadcrumb) {
            String normalized = text.strip().toUpperCase(Locale.ROOT);
            @Nullable PowerlineStyle semantic = switch (normalized) {
                case "FAIL", "FAILED", "ERROR", "ABORTED", "UNSUPPORTED",
                        "CONTRADICTED" -> FAILURE;
                case "SKIP", "SKIPPED", "NOT_EVALUATED",
                        "PARTIALLY_GROUNDED" -> WARNING;
                case "PASS", "PASSED", "SUCCESS", "RUN", "RUNNING", "EXECUTE", "EVALUATION",
                        "EVALUATING", "GROUNDED", "COMPLETED" -> STATUS;
                default -> httpStatus(normalized);
            };
            if (semantic != null) {
                return semantic;
            }
            return httpBreadcrumb && index == 3 ? DURATION : at(index);
        }

        private static @Nullable PowerlineStyle httpStatus(String text) {
            if (!text.startsWith("HTTP ")) {
                return null;
            }
            String statusText = text.substring("HTTP ".length()).strip();
            if (statusText.length() != 3 || !statusText.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int status = Integer.parseInt(statusText);
            if (status >= 500) {
                return FAILURE;
            }
            if (status >= 400) {
                return WARNING;
            }
            return status >= 200 ? STATUS : null;
        }
    }

    enum TextStyle {
        HEADING(ansi -> ansi.fgCyan().bold()),
        STRONG(ansi -> ansi.fgYellow().bold()),
        EMPHASIS(ansi -> ansi.a(Ansi.Attribute.ITALIC)),
        INLINE_CODE(Ansi::fgMagenta),
        CODE_BLOCK(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBright(Ansi.Color.WHITE)),
        CODE_KEYWORD(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBrightMagenta().bold()),
        CODE_STRING(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBrightGreen()),
        CODE_NUMBER(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBrightCyan()),
        CODE_COMMENT(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBrightBlack()
                .a(Ansi.Attribute.ITALIC)),
        CODE_PROPERTY(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBrightBlue()),
        CODE_VARIABLE(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBrightYellow()),
        CODE_OPERATOR(ansi -> ansi.bgBright(Ansi.Color.BLACK).fgBright(Ansi.Color.WHITE)),
        TABLE_BORDER(ansi -> ansi.fgBrightBlack().a(Ansi.Attribute.INTENSITY_FAINT)),
        TABLE_HEADER(ansi -> ansi.fgCyan().bold()),
        LIST_MARKER(ansi -> ansi.fgGreen().bold()),
        QUOTE_MARKER(Ansi::fgBlue),
        LINK(Ansi::fgBlue);

        private final Consumer<Ansi> decoration;

        TextStyle(Consumer<Ansi> decoration) {
            this.decoration = decoration;
        }

        void apply(Ansi ansi) {
            decoration.accept(ansi);
        }
    }
}
