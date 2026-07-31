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

import java.util.List;

/** Immutable, layout-independent representation of one parsed Markdown table. */
record TerminalTable(
        List<Row> headerRows,
        List<Row> bodyRows,
        List<Alignment> alignments) {

    TerminalTable {
        headerRows = List.copyOf(headerRows);
        bodyRows = List.copyOf(bodyRows);
        alignments = List.copyOf(alignments);
    }

    enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }

    record Row(List<Cell> cells) {

        Row {
            cells = List.copyOf(cells);
        }
    }

    record Cell(List<Fragment> fragments) {

        private static final Cell EMPTY = new Cell(List.of());

        Cell {
            fragments = List.copyOf(fragments);
        }

        static Cell empty() {
            return EMPTY;
        }
    }

    record Fragment(String text, List<AnsiStyler.TextStyle> styles) {

        Fragment {
            styles = List.copyOf(styles);
        }
    }
}
