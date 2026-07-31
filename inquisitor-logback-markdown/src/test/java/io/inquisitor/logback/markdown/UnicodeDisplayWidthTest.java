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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UnicodeDisplayWidthTest {

    private final DisplayWidth width = UnicodeDisplayWidth.INSTANCE;

    @Test
    void measuresAsciiCombiningCjkEmojiAndAnsiInTerminalColumns() {
        assertEquals(5, width.width("ASCII"));
        assertEquals(1, width.width("e\u0301"));
        assertEquals(4, width.width("模型"));
        assertEquals(2, width.width("🙂"));
        assertEquals(2, width.width("👨‍👩‍👧‍👦"));
        assertEquals(3, width.width("\u001B[1;36mabc\u001B[0m"));
    }
}
