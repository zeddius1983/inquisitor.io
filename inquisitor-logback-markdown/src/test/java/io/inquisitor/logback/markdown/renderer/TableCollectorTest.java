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

package io.inquisitor.logback.markdown.renderer;

import static io.inquisitor.logback.markdown.ansi.AnsiStyler.TextStyle.INLINE_CODE;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.TextStyle.LINK;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.TextStyle.STRONG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import org.junit.jupiter.api.Test;

class TableCollectorTest {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();

    @Test
    void collectsRowsAlignmentAndStyledInlineFragments() {
        Node document = PARSER.parse("""
                | Left | Center | Right |
                |:-----|:------:|------:|
                | AT&amp;T | **bold** and `code` | [docs](https://example.test) |
                """);

        TerminalTable table = TableCollector.collect((TableBlock) document.getFirstChild());

        assertEquals(List.of(
                TerminalTable.Alignment.LEFT,
                TerminalTable.Alignment.CENTER,
                TerminalTable.Alignment.RIGHT), table.alignments());
        assertEquals(1, table.headerRows().size());
        assertEquals(1, table.bodyRows().size());
        assertEquals(List.of(new TerminalTable.Fragment("AT&T", List.of())),
                table.bodyRows().getFirst().cells().getFirst().fragments());
        assertEquals(List.of(
                new TerminalTable.Fragment("bold", List.of(STRONG)),
                new TerminalTable.Fragment(" and ", List.of()),
                new TerminalTable.Fragment("code", List.of(INLINE_CODE))),
                table.bodyRows().getFirst().cells().get(1).fragments());
        assertEquals(List.of(new TerminalTable.Fragment(
                        "docs (https://example.test)", List.of(LINK))),
                table.bodyRows().getFirst().cells().get(2).fragments());
    }
}
