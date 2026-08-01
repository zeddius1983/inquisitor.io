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

package io.inquisitor.logback.markdown.highlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class BuiltinSyntaxHighlighterTest {

    private final SyntaxHighlighter highlighter = BuiltinSyntaxHighlighter.INSTANCE;

    @Test
    void highlightsJsonWithoutChangingSource() {
        String source = "{\"name\":\"Bob\",\"balance\":1000,\"active\":true}";

        List<SyntaxHighlighter.Span> spans = highlighter.highlight("json", source);

        assertEquals(source, join(spans));
        assertTrue(styles(spans).containsAll(List.of(
                        SyntaxHighlighter.Style.PROPERTY,
                        SyntaxHighlighter.Style.STRING,
                        SyntaxHighlighter.Style.NUMBER,
                        SyntaxHighlighter.Style.KEYWORD,
                        SyntaxHighlighter.Style.OPERATOR)));
    }

    @Test
    void highlightsSqlKeywordsStringsNumbersAndComments() {
        String source = "SELECT id FROM account WHERE owner = 'Bob' AND balance > 100 -- check";

        List<SyntaxHighlighter.Span> spans = highlighter.highlight("sql", source);

        assertEquals(source, join(spans));
        assertTrue(styles(spans).containsAll(List.of(
                        SyntaxHighlighter.Style.KEYWORD,
                        SyntaxHighlighter.Style.STRING,
                        SyntaxHighlighter.Style.NUMBER,
                        SyntaxHighlighter.Style.COMMENT)));
    }

    @Test
    void highlightsHttpMethodsStatusCodesAndHeaders() {
        assertEquals(SyntaxHighlighter.Style.KEYWORD,
                highlighter.highlight("http", "POST /accounts HTTP/1.1").getFirst().style());
        assertTrue(highlighter.highlight("http", "HTTP/1.1 201 Created").stream()
                .anyMatch(span -> span.style() == SyntaxHighlighter.Style.NUMBER));
        assertEquals(SyntaxHighlighter.Style.PROPERTY,
                highlighter.highlight("http", "Content-Type: application/json").getFirst().style());
    }

    @Test
    void supportsJavaAndShellAndLeavesUnknownLanguagesPlain() {
        List<SyntaxHighlighter.Span> java = highlighter.highlight(
                "java", "public record Account(long id) {} // immutable");
        List<SyntaxHighlighter.Span> shell = highlighter.highlight(
                "bash", "if [ \"$STATUS\" = ok ]; then echo done; fi");
        List<SyntaxHighlighter.Span> unknown = highlighter.highlight("klingon", "Qapla'");

        assertTrue(styles(java).containsAll(List.of(
                SyntaxHighlighter.Style.KEYWORD, SyntaxHighlighter.Style.COMMENT)));
        assertTrue(styles(shell).containsAll(List.of(
                SyntaxHighlighter.Style.KEYWORD, SyntaxHighlighter.Style.STRING)));
        assertEquals(List.of(
                new SyntaxHighlighter.Span("Qapla'", SyntaxHighlighter.Style.PLAIN)), unknown);
    }

    private static String join(List<SyntaxHighlighter.Span> spans) {
        return spans.stream().map(SyntaxHighlighter.Span::text)
                .collect(Collectors.joining());
    }

    private static List<SyntaxHighlighter.Style> styles(List<SyntaxHighlighter.Span> spans) {
        return spans.stream().map(SyntaxHighlighter.Span::style).toList();
    }
}
