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

import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.TABLE_BORDER;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.TABLE_HEADER;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Measures, wraps, and emits an immutable {@link TerminalTable}. */
final class TerminalTableRenderer {

    private final AnsiStyler styler;
    private final DisplayWidth displayWidth;

    TerminalTableRenderer(AnsiStyler styler, DisplayWidth displayWidth) {
        this.styler = styler;
        this.displayWidth = displayWidth;
    }

    List<String> render(TerminalTable table, int availableWidth) {
        int columns = columnCount(table);
        if (columns == 0) {
            return List.of();
        }
        int[] widths = allocateWidths(table, columns, availableWidth);
        List<TerminalTable.Alignment> alignments = normalizeAlignments(table, columns);
        List<String> lines = new ArrayList<>();
        lines.add(border("┌", "┬", "┐", widths));
        appendRows(lines, table.headerRows(), widths, alignments, true);
        if (!table.headerRows().isEmpty() && !table.bodyRows().isEmpty()) {
            lines.add(border("├", "┼", "┤", widths));
        }
        appendRows(lines, table.bodyRows(), widths, alignments, false);
        lines.add(border("└", "┴", "┘", widths));
        return List.copyOf(lines);
    }

    private void appendRows(
            List<String> output,
            List<TerminalTable.Row> rows,
            int[] widths,
            List<TerminalTable.Alignment> alignments,
            boolean header) {
        for (TerminalTable.Row row : rows) {
            List<List<StyledLine>> cells = new ArrayList<>(widths.length);
            int rowHeight = 1;
            for (int column = 0; column < widths.length; column++) {
                List<StyledLine> wrapped = wrap(cell(row, column), widths[column]);
                cells.add(wrapped);
                rowHeight = Math.max(rowHeight, wrapped.size());
            }
            for (int line = 0; line < rowHeight; line++) {
                output.add(rowLine(cells, line, widths, alignments, header));
            }
        }
    }

    private String rowLine(
            List<List<StyledLine>> cells,
            int lineIndex,
            int[] widths,
            List<TerminalTable.Alignment> alignments,
            boolean header) {
        StringBuilder output = new StringBuilder();
        appendBorder(output, "│");
        for (int column = 0; column < widths.length; column++) {
            StyledLine line = lineIndex < cells.get(column).size()
                    ? cells.get(column).get(lineIndex)
                    : StyledLine.empty();
            int remaining = Math.max(0, widths[column] - line.width());
            int left = switch (alignments.get(column)) {
                case LEFT -> 0;
                case CENTER -> remaining / 2;
                case RIGHT -> remaining;
            };
            int right = remaining - left;

            output.append(' ');
            if (header) {
                output.append(styler.open(TABLE_HEADER));
            }
            output.append(" ".repeat(left));
            appendContent(output, line.glyphs(), header);
            output.append(" ".repeat(right));
            if (header) {
                output.append(styler.reset());
            }
            output.append(' ');
            appendBorder(output, "│");
        }
        return output.toString();
    }

    private void appendContent(
            StringBuilder output,
            List<Glyph> glyphs,
            boolean header) {
        int index = 0;
        while (index < glyphs.size()) {
            Glyph first = glyphs.get(index);
            int end = index + 1;
            while (end < glyphs.size() && glyphs.get(end).styles().equals(first.styles())) {
                end++;
            }
            if (!first.styles().isEmpty()) {
                output.append(styler.reset());
                if (header) {
                    output.append(styler.open(TABLE_HEADER));
                }
                for (AnsiStyler.TextStyle style : first.styles()) {
                    output.append(styler.open(style));
                }
            }
            for (int glyphIndex = index; glyphIndex < end; glyphIndex++) {
                output.append(glyphs.get(glyphIndex).text());
            }
            if (!first.styles().isEmpty()) {
                output.append(styler.reset());
                if (header) {
                    output.append(styler.open(TABLE_HEADER));
                }
            }
            index = end;
        }
    }

    private String border(String left, String junction, String right, int[] widths) {
        StringBuilder border = new StringBuilder(left);
        for (int column = 0; column < widths.length; column++) {
            if (column > 0) {
                border.append(junction);
            }
            border.append("─".repeat(widths[column] + 2));
        }
        border.append(right);
        return styler.open(TABLE_BORDER) + border + styler.reset();
    }

    private void appendBorder(StringBuilder output, String border) {
        output.append(styler.open(TABLE_BORDER)).append(border).append(styler.reset());
    }

    private List<StyledLine> wrap(TerminalTable.Cell cell, int width) {
        List<Glyph> glyphs = glyphs(cell);
        if (glyphs.isEmpty()) {
            return List.of(StyledLine.empty());
        }

        List<StyledLine> lines = new ArrayList<>();
        int start = 0;
        while (start < glyphs.size()) {
            if (glyphs.get(start).lineBreak()) {
                lines.add(StyledLine.empty());
                start++;
                continue;
            }
            int used = 0;
            int lastWhitespace = -1;
            int end = start;
            while (end < glyphs.size() && !glyphs.get(end).lineBreak()) {
                Glyph glyph = glyphs.get(end);
                if (used + glyph.width() > width) {
                    break;
                }
                used += glyph.width();
                if (glyph.whitespace()) {
                    lastWhitespace = end;
                }
                end++;
            }

            if (end == glyphs.size()
                    || (end < glyphs.size() && glyphs.get(end).lineBreak())) {
                lines.add(line(glyphs, start, end));
                start = end < glyphs.size() ? end + 1 : end;
                continue;
            }
            if (end == start) {
                lines.add(line(glyphs, start, start + 1));
                start++;
                continue;
            }
            if (lastWhitespace >= start) {
                lines.add(line(glyphs, start, lastWhitespace));
                start = lastWhitespace + 1;
                while (start < glyphs.size()
                        && glyphs.get(start).whitespace()
                        && !glyphs.get(start).lineBreak()) {
                    start++;
                }
                continue;
            }
            lines.add(line(glyphs, start, end));
            start = end;
        }
        return List.copyOf(lines);
    }

    private StyledLine line(List<Glyph> glyphs, int start, int end) {
        while (end > start && glyphs.get(end - 1).whitespace()) {
            end--;
        }
        List<Glyph> content = List.copyOf(glyphs.subList(start, end));
        int width = content.stream().mapToInt(Glyph::width).sum();
        return new StyledLine(content, width);
    }

    private List<Glyph> glyphs(TerminalTable.Cell cell) {
        List<Glyph> glyphs = new ArrayList<>();
        for (TerminalTable.Fragment fragment : cell.fragments()) {
            BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
            graphemes.setText(fragment.text());
            int start = graphemes.first();
            for (int end = graphemes.next(); end != BreakIterator.DONE;
                    start = end, end = graphemes.next()) {
                String text = fragment.text().substring(start, end);
                boolean lineBreak = text.codePoints().anyMatch(
                        codePoint -> codePoint == '\n' || codePoint == '\r');
                boolean whitespace = !lineBreak && text.codePoints().allMatch(Character::isWhitespace);
                glyphs.add(new Glyph(text, displayWidth.width(text), whitespace,
                        lineBreak, fragment.styles()));
            }
        }
        return List.copyOf(glyphs);
    }

    private int[] allocateWidths(TerminalTable table, int columns, int availableWidth) {
        int[] natural = naturalWidths(table, columns);
        int naturalTableWidth = Arrays.stream(natural).sum() + 3 * columns + 1;
        if (naturalTableWidth <= availableWidth || 3 * columns + 1 >= availableWidth) {
            return natural;
        }

        int contentBudget = availableWidth - 3 * columns - 1;
        if (contentBudget < columns) {
            return natural;
        }
        int[] allocated = new int[columns];
        Arrays.fill(allocated, 1);
        int remaining = contentBudget - columns;
        while (remaining > 0) {
            boolean expanded = false;
            for (int column = 0; column < columns && remaining > 0; column++) {
                if (allocated[column] < natural[column]) {
                    allocated[column]++;
                    remaining--;
                    expanded = true;
                }
            }
            if (!expanded) {
                break;
            }
        }
        return allocated;
    }

    private int[] naturalWidths(TerminalTable table, int columns) {
        int[] widths = new int[columns];
        Arrays.fill(widths, 1);
        for (TerminalTable.Row row : allRows(table)) {
            for (int column = 0; column < row.cells().size(); column++) {
                widths[column] = Math.max(widths[column], cellWidth(row.cells().get(column)));
            }
        }
        return widths;
    }

    private int cellWidth(TerminalTable.Cell cell) {
        String text = cell.fragments().stream()
                .map(TerminalTable.Fragment::text)
                .collect(Collectors.joining());
        return Arrays.stream(text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1))
                .mapToInt(displayWidth::width)
                .max()
                .orElse(0);
    }

    private static int columnCount(TerminalTable table) {
        int rowColumns = allRows(table).stream()
                .mapToInt(row -> row.cells().size())
                .max()
                .orElse(0);
        return Math.max(rowColumns, table.alignments().size());
    }

    private static List<TerminalTable.Row> allRows(TerminalTable table) {
        List<TerminalTable.Row> rows = new ArrayList<>(
                table.headerRows().size() + table.bodyRows().size());
        rows.addAll(table.headerRows());
        rows.addAll(table.bodyRows());
        return rows;
    }

    private static List<TerminalTable.Alignment> normalizeAlignments(
            TerminalTable table,
            int columns) {
        List<TerminalTable.Alignment> alignments = new ArrayList<>(columns);
        for (int column = 0; column < columns; column++) {
            alignments.add(column < table.alignments().size()
                    ? table.alignments().get(column)
                    : TerminalTable.Alignment.LEFT);
        }
        return List.copyOf(alignments);
    }

    private static TerminalTable.Cell cell(TerminalTable.Row row, int column) {
        return column < row.cells().size() ? row.cells().get(column) : TerminalTable.Cell.empty();
    }

    private record Glyph(
            String text,
            int width,
            boolean whitespace,
            boolean lineBreak,
            List<AnsiStyler.TextStyle> styles) {

        Glyph {
            styles = List.copyOf(styles);
        }
    }

    private record StyledLine(List<Glyph> glyphs, int width) {

        private static final StyledLine EMPTY = new StyledLine(List.of(), 0);

        StyledLine {
            glyphs = List.copyOf(glyphs);
        }

        static StyledLine empty() {
            return EMPTY;
        }
    }
}
