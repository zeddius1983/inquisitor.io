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

import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_BLOCK;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.EMPHASIS;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.HEADING;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.INLINE_CODE;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.LINK;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.LIST_MARKER;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.QUOTE_MARKER;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.STRONG;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;

import com.vladsch.flexmark.ast.AutoLink;
import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.BulletListItem;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.HtmlEntity;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListBlock;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.OrderedListItem;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.ast.Visitor;

/** Renders a practical Markdown subset as ANSI-styled terminal text. */
public final class FlexmarkAnsiRenderer implements MarkdownRenderer {

    private static final Parser PARSER = Parser.builder().build();

    private final AnsiStyler styler;

    /**
     * Creates a renderer that emits ANSI only when an interactive, color-capable
     * terminal is detected.
     */
    public FlexmarkAnsiRenderer() {
        this(AnsiSupport.isAutoEnabled());
    }

    /**
     * Creates a renderer with an explicit ANSI policy.
     *
     * @param ansiEnabled whether ANSI style sequences should be emitted
     */
    public FlexmarkAnsiRenderer(boolean ansiEnabled) {
        this.styler = new AnsiStyler(ansiEnabled);
    }

    /**
     * Parses and renders one Markdown document. Mutable traversal state belongs to
     * this call, so a renderer instance can be shared safely.
     *
     * @param markdown Markdown source
     * @return terminal-oriented text
     */
    @Override
    public String render(String markdown) {
        Node document = PARSER.parse(markdown);
        return new RenderingContext(styler).render(document);
    }

    private static final class RenderingContext {

        private final StringBuilder output = new StringBuilder();
        private final Deque<AnsiStyler.TextStyle> styles = new ArrayDeque<>();
        private final Deque<ListState> lists = new ArrayDeque<>();
        private final AnsiStyler styler;
        private final NodeVisitor visitor;

        private int quoteDepth;
        private boolean lineStart = true;

        RenderingContext(AnsiStyler styler) {
            this.styler = styler;
            this.visitor = new NodeVisitor(
                    new VisitHandler<>(Heading.class, this::visit),
                    new VisitHandler<>(Paragraph.class, this::visit),
                    new VisitHandler<>(StrongEmphasis.class, this::visit),
                    new VisitHandler<>(Emphasis.class, this::visit),
                    new VisitHandler<>(Code.class, this::visit),
                    new VisitHandler<>(FencedCodeBlock.class, this::visit),
                    new VisitHandler<>(IndentedCodeBlock.class, this::visit),
                    new VisitHandler<>(BulletList.class, this::visit),
                    new VisitHandler<>(OrderedList.class, this::visit),
                    new VisitHandler<>(BulletListItem.class, this::visit),
                    new VisitHandler<>(OrderedListItem.class, this::visit),
                    new VisitHandler<>(BlockQuote.class, this::visit),
                    new VisitHandler<>(Link.class, this::visit),
                    new VisitHandler<>(AutoLink.class, this::visit),
                    new VisitHandler<>(MailLink.class, this::visit),
                    new VisitHandler<>(Image.class, this::visit),
                    new VisitHandler<>(HtmlEntity.class, this::visit),
                    new VisitHandler<>(ThematicBreak.class, this::visit),
                    new VisitHandler<>(SoftLineBreak.class, this::visit),
                    new VisitHandler<>(HardLineBreak.class, this::visit),
                    new VisitHandler<>(Text.class, this::visit)) {
                @Override
                protected void processNode(
                        Node node,
                        boolean withChildren,
                        BiConsumer<Node, Visitor<Node>> processor) {
                    if (withChildren && getHandler(node) == null && !node.hasChildren()) {
                        append(node.getChars());
                        return;
                    }
                    super.processNode(node, withChildren, processor);
                }
            };
        }

        String render(Node document) {
            visitor.visit(document);
            while (!output.isEmpty() && isLineBreak(output.charAt(output.length() - 1))) {
                output.deleteCharAt(output.length() - 1);
            }
            return output.toString();
        }

        private void visit(Heading heading) {
            separateBlock();
            styled(HEADING, () -> visitor.visitChildren(heading));
            endBlock();
        }

        private void visit(Paragraph paragraph) {
            if (!(paragraph.getParent() instanceof ListItem)) {
                separateBlock();
            }
            visitor.visitChildren(paragraph);
            if (!(paragraph.getParent() instanceof ListItem)) {
                endBlock();
            }
        }

        private void visit(StrongEmphasis strong) {
            styled(STRONG, () -> visitor.visitChildren(strong));
        }

        private void visit(Emphasis emphasis) {
            styled(EMPHASIS, () -> visitor.visitChildren(emphasis));
        }

        private void visit(Code code) {
            styled(INLINE_CODE, () -> append(code.getText()));
        }

        private void visit(FencedCodeBlock codeBlock) {
            renderCodeBlock(codeBlock, codeBlock.getContentChars().toString());
        }

        private void visit(IndentedCodeBlock codeBlock) {
            renderCodeBlock(codeBlock, codeBlock.getContentChars().toString());
        }

        private void visit(BulletList list) {
            renderList(list, new ListState(false, 1, '.'));
        }

        private void visit(OrderedList list) {
            renderList(list, new ListState(true, list.getStartNumber(), list.getDelimiter()));
        }

        private void renderList(ListBlock list, ListState state) {
            boolean nested = list.getParent() instanceof ListItem;
            if (nested) {
                ensureNewline();
            }
            else {
                separateBlock();
            }
            lists.push(state);
            visitor.visitChildren(list);
            lists.pop();
            if (!nested) {
                endBlock();
            }
        }

        private void visit(BulletListItem item) {
            renderListItem(item);
        }

        private void visit(OrderedListItem item) {
            renderListItem(item);
        }

        private void renderListItem(ListItem item) {
            ensureLineStart();
            append("  ".repeat(Math.max(0, lists.size() - 1)));
            styled(LIST_MARKER, () -> append(lists.element().nextMarker()));

            Node child = item.getFirstChild();
            boolean firstBlock = true;
            while (child != null) {
                Node next = child.getNext();
                if (!firstBlock && child instanceof Paragraph) {
                    ensureNewline();
                    append("  ".repeat(lists.size()));
                }
                visitor.visit(child);
                firstBlock = false;
                child = next;
            }
            ensureNewline();
        }

        private void visit(BlockQuote quote) {
            separateBlock();
            quoteDepth++;
            visitor.visitChildren(quote);
            quoteDepth--;
            endBlock();
        }

        private void visit(Link link) {
            styled(LINK, () -> visitor.visitChildren(link));
            String url = link.getUrl().unescape().toString();
            if (!url.isBlank() && !url.equals(link.getText().toString())) {
                append(" (");
                styled(LINK, () -> append(url));
                append(")");
            }
        }

        private void visit(AutoLink link) {
            styled(LINK, () -> append(link.getText().unescape()));
        }

        private void visit(MailLink link) {
            styled(LINK, () -> append(link.getText().unescape()));
        }

        private void visit(Image image) {
            String alt = image.getText().unescape().toString();
            String url = image.getUrl().unescape().toString();
            append(alt.isBlank() ? "image" : alt);
            if (!url.isBlank() && !url.equals(alt)) {
                append(" (");
                styled(LINK, () -> append(url));
                append(")");
            }
        }

        private void visit(HtmlEntity entity) {
            append(entity.getChars().unescape());
        }

        private void visit(ThematicBreak ignored) {
            separateBlock();
            append("────────");
            endBlock();
        }

        private void visit(SoftLineBreak ignored) {
            ensureNewline();
        }

        private void visit(HardLineBreak ignored) {
            ensureNewline();
        }

        private void visit(Text text) {
            append(text.getChars().unescape());
        }

        private void renderCodeBlock(Node codeBlock, String content) {
            boolean nested = codeBlock.getParent() instanceof ListItem;
            if (nested) {
                ensureNewline();
            }
            else {
                separateBlock();
            }

            String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
            if (normalized.endsWith("\n")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            String[] lines = normalized.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    ensureNewline();
                }
                if (nested) {
                    append("  ".repeat(lists.size()));
                }
                String line = lines[index];
                styled(CODE_BLOCK, () -> append(line));
            }
            endBlock();
        }

        private void styled(AnsiStyler.TextStyle style, Runnable content) {
            append(styler.open(style));
            styles.push(style);
            content.run();
            styles.pop();
            append(styler.reset());
            if (!styles.isEmpty()) {
                append(styler.open(styles.element()));
            }
        }

        private void append(CharSequence text) {
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (lineStart && !isLineBreak(character)) {
                    appendQuotePrefix();
                }
                output.append(character);
                lineStart = isLineBreak(character);
            }
        }

        private void appendQuotePrefix() {
            for (int depth = 0; depth < quoteDepth; depth++) {
                output.append(styler.open(QUOTE_MARKER)).append("│ ").append(styler.reset());
                if (!styles.isEmpty()) {
                    output.append(styler.open(styles.element()));
                }
            }
            lineStart = false;
        }

        private void appendQuoteBlankLinePrefix() {
            for (int depth = 0; depth < quoteDepth; depth++) {
                output.append(styler.open(QUOTE_MARKER)).append("│").append(styler.reset());
                if (depth + 1 < quoteDepth) {
                    output.append(' ');
                }
                if (!styles.isEmpty()) {
                    output.append(styler.open(styles.element()));
                }
            }
            lineStart = false;
        }

        private void separateBlock() {
            if (output.isEmpty()) {
                return;
            }
            ensureNewline();
            if (trailingLineBreaks() < 2) {
                if (quoteDepth > 0) {
                    appendQuoteBlankLinePrefix();
                }
                output.append('\n');
                lineStart = true;
            }
        }

        private void endBlock() {
            ensureNewline();
        }

        private void ensureLineStart() {
            if (!lineStart) {
                ensureNewline();
            }
        }

        private void ensureNewline() {
            if (!lineStart) {
                output.append('\n');
                lineStart = true;
            }
        }

        private int trailingLineBreaks() {
            int count = 0;
            for (int index = output.length() - 1;
                    index >= 0 && isLineBreak(output.charAt(index)); index--) {
                count++;
            }
            return count;
        }

        private static boolean isLineBreak(char character) {
            return character == '\n' || character == '\r';
        }
    }

    private static final class ListState {

        private final boolean ordered;
        private final char delimiter;
        private int nextNumber;

        ListState(boolean ordered, int nextNumber, char delimiter) {
            this.ordered = ordered;
            this.nextNumber = nextNumber;
            this.delimiter = delimiter;
        }

        String nextMarker() {
            if (!ordered) {
                return "• ";
            }
            return nextNumber++ + Character.toString(delimiter) + " ";
        }
    }
}
