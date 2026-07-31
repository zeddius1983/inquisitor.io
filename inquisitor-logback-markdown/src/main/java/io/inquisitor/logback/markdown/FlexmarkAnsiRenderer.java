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
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_COMMENT;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_KEYWORD;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_NUMBER;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_OPERATOR;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_PROPERTY;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_STRING;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.CODE_VARIABLE;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.EMPHASIS;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.HEADING;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.INLINE_CODE;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.LINK;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.LIST_MARKER;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.QUOTE_MARKER;
import static io.inquisitor.logback.markdown.AnsiStyler.TextStyle.STRONG;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.ListBlock;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.MailLink;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.OrderedListItem;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.Reference;
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

    private static final int LIST_LEFT_INDENT = 1;
    private static final int MAX_HIGHLIGHTED_CODE_CHARS = 16_384;
    private static final String POWERLINE_LEFT_CAP = "";
    private static final String POWERLINE_SEPARATOR = "";
    private static final String POWERLINE_RIGHT_CAP = "";

    private static final Parser PARSER = Parser.builder().build();

    private final AnsiStyler styler;
    private final SyntaxHighlighter syntaxHighlighter;

    /**
     * Creates a renderer that emits ANSI only when an interactive, color-capable
     * terminal is detected.
     */
    public FlexmarkAnsiRenderer() {
        this(AnsiSupport.isAutoEnabled(), BuiltinSyntaxHighlighter.INSTANCE);
    }

    /**
     * Creates a renderer with an explicit ANSI policy.
     *
     * @param ansiEnabled whether ANSI style sequences should be emitted
     */
    public FlexmarkAnsiRenderer(boolean ansiEnabled) {
        this(ansiEnabled, BuiltinSyntaxHighlighter.INSTANCE);
    }

    /**
     * Creates a renderer with explicit ANSI and syntax-highlighting policies.
     *
     * @param ansiEnabled whether ANSI style sequences should be emitted
     * @param syntaxHighlighter thread-safe code-fence highlighter
     */
    public FlexmarkAnsiRenderer(
            boolean ansiEnabled,
            SyntaxHighlighter syntaxHighlighter) {
        this.styler = new AnsiStyler(ansiEnabled);
        this.syntaxHighlighter = syntaxHighlighter;
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
        return new RenderingContext(styler, syntaxHighlighter).render(document);
    }

    private static final class RenderingContext {

        private final StringBuilder output = new StringBuilder();
        private final Deque<AnsiStyler.TextStyle> styles = new ArrayDeque<>();
        private final Deque<ListState> lists = new ArrayDeque<>();
        private final Deque<Integer> listItemContentIndents = new ArrayDeque<>();
        private final AnsiStyler styler;
        private final SyntaxHighlighter syntaxHighlighter;
        private final NodeVisitor visitor;

        private int quoteDepth;
        private boolean lineStart = true;

        RenderingContext(AnsiStyler styler, SyntaxHighlighter syntaxHighlighter) {
            this.styler = styler;
            this.syntaxHighlighter = syntaxHighlighter;
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
                    new VisitHandler<>(LinkRef.class, this::visit),
                    new VisitHandler<>(Reference.class, this::visit),
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
            Optional<List<String>> powerline = powerlineSegments(heading);
            if (styler.isEnabled() && powerline.isPresent()) {
                renderPowerline(powerline.orElseThrow());
            }
            else {
                styled(HEADING, () -> visitor.visitChildren(heading));
            }
            endBlock();
        }

        private void renderPowerline(List<String> segments) {
            AnsiStyler.PowerlineStyle first = powerlineStyle(segments, 0);
            append(styler.powerlineCap(first, POWERLINE_LEFT_CAP));
            for (int index = 0; index < segments.size(); index++) {
                AnsiStyler.PowerlineStyle style = powerlineStyle(segments, index);
                if (index > 0) {
                    AnsiStyler.PowerlineStyle previous =
                            powerlineStyle(segments, index - 1);
                    append(styler.powerlineTransition(
                            previous, style, POWERLINE_SEPARATOR));
                }
                append(styler.open(style));
                append(" " + segments.get(index) + " ");
                append(styler.reset());
            }
            AnsiStyler.PowerlineStyle last =
                    powerlineStyle(segments, segments.size() - 1);
            append(styler.powerlineCap(last, POWERLINE_RIGHT_CAP));
        }

        private static AnsiStyler.PowerlineStyle powerlineStyle(
                List<String> segments,
                int index) {
            return AnsiStyler.PowerlineStyle.forSegment(
                    index, segments.get(index));
        }

        private static Optional<List<String>> powerlineSegments(Heading heading) {
            String text = heading.getText().unescape().toString().strip();
            if (!text.startsWith(POWERLINE_LEFT_CAP)
                    || !text.endsWith(POWERLINE_RIGHT_CAP)) {
                return Optional.empty();
            }
            String content = text.substring(
                    POWERLINE_LEFT_CAP.length(),
                    text.length() - POWERLINE_RIGHT_CAP.length());
            List<String> segments = Arrays.stream(content.split(POWERLINE_SEPARATOR, -1))
                    .map(String::strip)
                    .toList();
            return segments.size() > 1 && segments.stream().noneMatch(String::isEmpty)
                    ? Optional.of(segments)
                    : Optional.empty();
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
            String language = codeBlock.getInfo().unescape().toString().strip();
            int separator = firstWhitespace(language);
            renderCodeBlock(codeBlock, codeBlock.getContentChars().toString(),
                    separator < 0 ? language : language.substring(0, separator));
        }

        private void visit(IndentedCodeBlock codeBlock) {
            renderCodeBlock(codeBlock, codeBlock.getContentChars().toString(), "");
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
            int itemIndent = listItemContentIndents.isEmpty()
                    ? LIST_LEFT_INDENT
                    : listItemContentIndents.element();
            append(" ".repeat(itemIndent));
            String marker = lists.element().nextMarker();
            styled(LIST_MARKER, () -> append(marker));
            listItemContentIndents.push(itemIndent + marker.length());

            Node child = item.getFirstChild();
            boolean firstBlock = true;
            try {
                while (child != null) {
                    Node next = child.getNext();
                    if (!firstBlock && child instanceof Paragraph) {
                        ensureNewline();
                        appendListItemContentIndent();
                    }
                    visitor.visit(child);
                    firstBlock = false;
                    child = next;
                }
            }
            finally {
                listItemContentIndents.pop();
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
            String url = link.getUrl().unescape().toString();
            renderLink(link, url, link.getText().unescape().toString());
        }

        private void visit(LinkRef link) {
            Reference reference = link.getReferenceNode(link.getDocument());
            if (reference == null) {
                append(link.getChars());
                return;
            }
            String label = (link.isReferenceTextCombined()
                    ? link.getReference()
                    : link.getText()).unescape().toString();
            renderLink(link, reference.getUrl().unescape().toString(), label);
        }

        private void visit(Reference ignored) {
            // Reference definitions are Markdown metadata, not visible content.
        }

        private void renderLink(Node link, String url, String label) {
            styled(LINK, () -> visitor.visitChildren(link));
            if (url.isBlank() || url.equals(label)) {
                return;
            }
            append(" (");
            styled(LINK, () -> append(url));
            append(")");
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
            appendListItemContentIndent();
        }

        private void visit(HardLineBreak ignored) {
            ensureNewline();
            appendListItemContentIndent();
        }

        private void visit(Text text) {
            append(text.getChars().unescape());
        }

        private void renderCodeBlock(Node codeBlock, String content, String language) {
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
            int panelWidth = styler.isEnabled()
                    ? Arrays.stream(lines).mapToInt(String::length).max().orElse(0)
                    : 0;
            boolean highlight = styler.isEnabled()
                    && normalized.length() <= MAX_HIGHLIGHTED_CODE_CHARS;
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    ensureNewline();
                }
                if (nested) {
                    appendListItemContentIndent();
                }
                String line = lines[index];
                if (!styler.isEnabled()) {
                    append(line);
                    continue;
                }
                List<SyntaxHighlighter.Span> spans = highlight
                        ? highlighted(language, line)
                        : plainCode(line);
                styled(CODE_BLOCK, () -> {
                    append(" ");
                    spans.forEach(this::appendCodeSpan);
                    append(" ".repeat(panelWidth - line.length() + 1));
                });
            }
            endBlock();
        }

        private List<SyntaxHighlighter.Span> highlighted(String language, String source) {
            try {
                List<SyntaxHighlighter.Span> spans = List.copyOf(
                        syntaxHighlighter.highlight(language, source));
                String renderedSource = spans.stream()
                        .map(SyntaxHighlighter.Span::text)
                        .collect(Collectors.joining());
                return renderedSource.equals(source) ? spans : plainCode(source);
            }
            catch (RuntimeException | StackOverflowError exception) {
                return plainCode(source);
            }
        }

        private void appendCodeSpan(SyntaxHighlighter.Span span) {
            if (span.style() == SyntaxHighlighter.Style.PLAIN) {
                append(span.text());
                return;
            }
            styled(codeStyle(span.style()), () -> append(span.text()));
        }

        private static int firstWhitespace(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (Character.isWhitespace(value.charAt(index))) {
                    return index;
                }
            }
            return -1;
        }

        private static List<SyntaxHighlighter.Span> plainCode(String source) {
            return source.isEmpty()
                    ? List.of()
                    : List.of(new SyntaxHighlighter.Span(source, SyntaxHighlighter.Style.PLAIN));
        }

        private static AnsiStyler.TextStyle codeStyle(SyntaxHighlighter.Style style) {
            return switch (style) {
                case PLAIN -> CODE_BLOCK;
                case KEYWORD -> CODE_KEYWORD;
                case STRING -> CODE_STRING;
                case NUMBER -> CODE_NUMBER;
                case COMMENT -> CODE_COMMENT;
                case PROPERTY -> CODE_PROPERTY;
                case VARIABLE -> CODE_VARIABLE;
                case OPERATOR -> CODE_OPERATOR;
            };
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

        private void appendListItemContentIndent() {
            if (!listItemContentIndents.isEmpty()) {
                append(" ".repeat(listItemContentIndents.element()));
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
