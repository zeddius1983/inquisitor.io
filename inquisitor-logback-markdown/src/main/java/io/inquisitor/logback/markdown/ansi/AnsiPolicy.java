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

/** Controls whether terminal rendering emits ANSI escape sequences. */
public enum AnsiPolicy {
    /** Always emit ANSI styling. */
    ALWAYS,
    /** Never emit ANSI styling. */
    NEVER,
    /** Emit ANSI styling only when the current terminal supports it. */
    DETECT;

    /** Resolves this policy for the current process. */
    public boolean resolve() {
        return switch (this) {
            case ALWAYS -> true;
            case NEVER -> false;
            case DETECT -> AnsiSupport.isAutoEnabled();
        };
    }
}
