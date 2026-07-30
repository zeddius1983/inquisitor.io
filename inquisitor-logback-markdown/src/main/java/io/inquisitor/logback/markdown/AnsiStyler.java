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

import java.util.function.Consumer;

import org.jline.jansi.Ansi;

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

    enum TextStyle {
        HEADING(ansi -> ansi.fgCyan().bold()),
        STRONG(ansi -> ansi.fgYellow().bold()),
        EMPHASIS(ansi -> ansi.a(Ansi.Attribute.ITALIC)),
        INLINE_CODE(Ansi::fgMagenta),
        CODE_BLOCK(Ansi::fgBrightBlack),
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
