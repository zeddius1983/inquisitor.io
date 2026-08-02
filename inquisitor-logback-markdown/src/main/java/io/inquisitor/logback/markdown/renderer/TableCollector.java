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

import static io.inquisitor.logback.markdown.ansi.AnsiStyler.TextStyle.EMPHASIS;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.TextStyle.INLINE_CODE;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.TextStyle.LINK;
import static io.inquisitor.logback.markdown.ansi.AnsiStyler.TextStyle.STRONG;

import java.util.ArrayList;
import java.util.List;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.HtmlEntity;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableBody;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.util.ast.Node;
import io.inquisitor.logback.markdown.ansi.AnsiStyler;

/** Converts a Flexmark table subtree into the terminal table value model. */
final class TableCollector {

    private TableCollector() {
    }

    static TerminalTable collect(TableBlock tableBlock) {
        List<TerminalTable.Row> headerRows = new ArrayList<>();
        List<TerminalTable.Row> bodyRows = new ArrayList<>();
        List<TerminalTable.Alignment> alignments = new ArrayList<>();
        for (Node child = tableBlock.getFirstChild(); child != null; child = child.getNext()) {
            switch (child) {
                case TableHead head -> collectRows(head, headerRows, alignments);
                case TableBody body -> collectRows(body, bodyRows, alignments);
                default -> {
                }
            }
        }
        return new TerminalTable(headerRows, bodyRows, alignments);
    }

    private static void collectRows(
            Node section,
            List<TerminalTable.Row> rows,
            List<TerminalTable.Alignment> alignments) {
        for (Node child = section.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof TableRow row)) {
                continue;
            }
            List<TerminalTable.Cell> cells = new ArrayList<>();
            int column = 0;
            for (Node cellNode = row.getFirstChild(); cellNode != null;
                    cellNode = cellNode.getNext()) {
                if (!(cellNode instanceof TableCell cell)) {
                    continue;
                }
                cells.add(collectCell(cell));
                while (alignments.size() <= column) {
                    alignments.add(TerminalTable.Alignment.LEFT);
                }
                if (cell.getAlignment() != null) {
                    alignments.set(column, alignment(cell.getAlignment()));
                }
                column++;
            }
            rows.add(new TerminalTable.Row(cells));
        }
    }

    private static TerminalTable.Cell collectCell(TableCell cell) {
        List<TerminalTable.Fragment> fragments = new ArrayList<>();
        for (Node child = cell.getFirstChild(); child != null; child = child.getNext()) {
            collectInline(child, List.of(), fragments);
        }
        return new TerminalTable.Cell(fragments);
    }

    private static void collectInline(
            Node node,
            List<AnsiStyler.TextStyle> inlineStyles,
            List<TerminalTable.Fragment> fragments) {
        switch (node) {
            case Text text -> appendFragment(
                    fragments, text.getChars().unescape().toString(), inlineStyles);
            case HtmlEntity entity -> appendFragment(
                    fragments, entity.getChars().unescape().toString(), inlineStyles);
            case Code code -> appendFragment(
                    fragments, code.getText().toString(), withStyle(inlineStyles, INLINE_CODE));
            case StrongEmphasis ignored -> collectInlineChildren(
                    node, withStyle(inlineStyles, STRONG), fragments);
            case Emphasis ignored -> collectInlineChildren(
                    node, withStyle(inlineStyles, EMPHASIS), fragments);
            case Link link -> collectLink(link, inlineStyles, fragments);
            case LinkRef link -> collectReferenceLink(link, inlineStyles, fragments);
            case AutoLink link -> appendFragment(
                    fragments, link.getText().unescape().toString(), withStyle(inlineStyles, LINK));
            case MailLink link -> appendFragment(
                    fragments, link.getText().unescape().toString(), withStyle(inlineStyles, LINK));
            case Image image -> collectImage(image, inlineStyles, fragments);
            case SoftLineBreak ignored -> appendFragment(fragments, " ", inlineStyles);
            case HardLineBreak ignored -> appendFragment(fragments, " ", inlineStyles);
            default -> collectUnknown(node, inlineStyles, fragments);
        }
    }

    private static void collectLink(
            Link link,
            List<AnsiStyler.TextStyle> inlineStyles,
            List<TerminalTable.Fragment> fragments) {
        List<AnsiStyler.TextStyle> linkStyles = withStyle(inlineStyles, LINK);
        collectInlineChildren(link, linkStyles, fragments);
        appendLinkDestination(fragments, link.getUrl().unescape().toString(),
                link.getText().unescape().toString(), linkStyles);
    }

    private static void collectReferenceLink(
            LinkRef link,
            List<AnsiStyler.TextStyle> inlineStyles,
            List<TerminalTable.Fragment> fragments) {
        Reference reference = link.getReferenceNode(link.getDocument());
        if (reference == null) {
            appendFragment(fragments, link.getChars().unescape().toString(), inlineStyles);
            return;
        }
        List<AnsiStyler.TextStyle> linkStyles = withStyle(inlineStyles, LINK);
        collectInlineChildren(link, linkStyles, fragments);
        String label = (link.isReferenceTextCombined()
                ? link.getReference()
                : link.getText()).unescape().toString();
        appendLinkDestination(fragments,
                reference.getUrl().unescape().toString(), label, linkStyles);
    }

    private static void collectImage(
            Image image,
            List<AnsiStyler.TextStyle> inlineStyles,
            List<TerminalTable.Fragment> fragments) {
        String alt = image.getText().unescape().toString();
        String url = image.getUrl().unescape().toString();
        appendFragment(fragments, alt.isBlank() ? "image" : alt, inlineStyles);
        appendLinkDestination(fragments, url, alt, withStyle(inlineStyles, LINK));
    }

    private static void collectUnknown(
            Node node,
            List<AnsiStyler.TextStyle> inlineStyles,
            List<TerminalTable.Fragment> fragments) {
        if (node.hasChildren()) {
            collectInlineChildren(node, inlineStyles, fragments);
        }
        else {
            appendFragment(fragments, node.getChars().unescape().toString(), inlineStyles);
        }
    }

    private static void collectInlineChildren(
            Node parent,
            List<AnsiStyler.TextStyle> inlineStyles,
            List<TerminalTable.Fragment> fragments) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            collectInline(child, inlineStyles, fragments);
        }
    }

    private static void appendLinkDestination(
            List<TerminalTable.Fragment> fragments,
            String url,
            String label,
            List<AnsiStyler.TextStyle> styles) {
        if (!url.isBlank() && !url.equals(label)) {
            appendFragment(fragments, " (" + url + ")", styles);
        }
    }

    private static List<AnsiStyler.TextStyle> withStyle(
            List<AnsiStyler.TextStyle> styles,
            AnsiStyler.TextStyle style) {
        List<AnsiStyler.TextStyle> nested = new ArrayList<>(styles.size() + 1);
        nested.addAll(styles);
        nested.add(style);
        return List.copyOf(nested);
    }

    private static void appendFragment(
            List<TerminalTable.Fragment> fragments,
            String text,
            List<AnsiStyler.TextStyle> styles) {
        if (text.isEmpty()) {
            return;
        }
        if (!fragments.isEmpty()) {
            TerminalTable.Fragment last = fragments.getLast();
            if (last.styles().equals(styles)) {
                fragments.set(fragments.size() - 1,
                        new TerminalTable.Fragment(last.text() + text, styles));
                return;
            }
        }
        fragments.add(new TerminalTable.Fragment(text, styles));
    }

    private static TerminalTable.Alignment alignment(TableCell.Alignment alignment) {
        return switch (alignment) {
            case LEFT -> TerminalTable.Alignment.LEFT;
            case CENTER -> TerminalTable.Alignment.CENTER;
            case RIGHT -> TerminalTable.Alignment.RIGHT;
        };
    }
}
