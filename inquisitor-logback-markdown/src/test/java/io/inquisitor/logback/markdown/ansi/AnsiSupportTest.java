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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnsiSupportTest {

    @Test
    void autoEnablesAnsiOnlyForAnInteractiveCapableTerminal() {
        assertTrue(AnsiSupport.isAutoEnabled(null, "xterm-256color", true, true));
        assertTrue(AnsiSupport.isAutoEnabled("", "xterm-256color", true, true));

        assertFalse(AnsiSupport.isAutoEnabled(null, "xterm-256color", false, true));
        assertFalse(AnsiSupport.isAutoEnabled("1", "xterm-256color", true, true));
        assertFalse(AnsiSupport.isAutoEnabled(" ", "xterm-256color", true, true));
        assertFalse(AnsiSupport.isAutoEnabled(null, "dumb", true, true));
        assertFalse(AnsiSupport.isAutoEnabled(null, " DUMB ", true, true));
        assertFalse(AnsiSupport.isAutoEnabled(null, "xterm-256color", true, false));
    }
}
