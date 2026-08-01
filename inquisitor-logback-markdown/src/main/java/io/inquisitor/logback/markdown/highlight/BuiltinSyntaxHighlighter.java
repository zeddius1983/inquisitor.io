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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Lightweight, dependency-free highlighting for common harness code fences. */
public final class BuiltinSyntaxHighlighter implements SyntaxHighlighter {

    /** Shared stateless instance. */
    public static final BuiltinSyntaxHighlighter INSTANCE = new BuiltinSyntaxHighlighter();

    private static final Set<String> JSON_KEYWORDS = Set.of("true", "false", "null");
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "add", "alter", "and", "as", "asc", "begin", "between", "by", "case",
            "commit", "create", "delete", "desc", "distinct", "drop", "else", "end",
            "exists", "from", "full", "group", "having", "in", "index", "inner",
            "insert", "into", "is", "join", "left", "like", "limit", "not", "null",
            "offset", "on", "or", "order", "outer", "primary", "references", "returning",
            "right", "rollback", "select", "set", "table", "then", "truncate", "union",
            "unique", "update", "values", "when", "where", "with");
    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public", "record", "return",
            "sealed", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try", "var", "void",
            "volatile", "while", "yield");
    private static final Set<String> SHELL_KEYWORDS = Set.of(
            "case", "do", "done", "elif", "else", "esac", "export", "fi", "for",
            "function", "if", "in", "local", "readonly", "select", "then", "until",
            "while");
    private static final Set<String> HTTP_METHODS = Set.of(
            "CONNECT", "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT", "TRACE");

    private BuiltinSyntaxHighlighter() {
    }

    @Override
    public List<Span> highlight(String language, String source) {
        return switch (normalized(language)) {
            case "json", "jsonc" -> scan(source, Profile.JSON);
            case "sql", "postgresql", "postgres", "mysql" -> scan(source, Profile.SQL);
            case "java" -> scan(source, Profile.JAVA);
            case "bash", "sh", "shell", "zsh" -> scan(source, Profile.SHELL);
            case "http", "https" -> highlightHttp(source);
            default -> plain(source);
        };
    }

    private static List<Span> highlightHttp(String source) {
        if (source.isEmpty()) {
            return List.of();
        }
        int firstSpace = source.indexOf(' ');
        String first = firstSpace < 0 ? source : source.substring(0, firstSpace);
        if (HTTP_METHODS.contains(first.toUpperCase(Locale.ROOT))) {
            SpanBuilder spans = new SpanBuilder();
            spans.add(first, Style.KEYWORD);
            if (firstSpace >= 0) {
                spans.add(source.substring(firstSpace), Style.PLAIN);
            }
            return spans.result();
        }
        if (source.startsWith("HTTP/")) {
            int statusStart = source.indexOf(' ');
            if (statusStart > 0) {
                int statusEnd = nextSpace(source, statusStart + 1);
                SpanBuilder spans = new SpanBuilder();
                spans.add(source.substring(0, statusStart), Style.KEYWORD);
                spans.add(" ", Style.PLAIN);
                spans.add(source.substring(statusStart + 1, statusEnd), Style.NUMBER);
                spans.add(source.substring(statusEnd), Style.PLAIN);
                return spans.result();
            }
        }
        int colon = source.indexOf(':');
        if (colon > 0 && source.substring(0, colon).chars()
                .allMatch(character -> Character.isLetterOrDigit(character) || character == '-')) {
            return List.of(
                    new Span(source.substring(0, colon), Style.PROPERTY),
                    new Span(source.substring(colon), Style.PLAIN));
        }
        return plain(source);
    }

    private static List<Span> scan(String source, Profile profile) {
        SpanBuilder spans = new SpanBuilder();
        int cursor = 0;
        while (cursor < source.length()) {
            int commentEnd = commentEnd(source, cursor, profile);
            if (commentEnd >= 0) {
                spans.add(source.substring(cursor, commentEnd), Style.COMMENT);
                cursor = commentEnd;
                continue;
            }
            char character = source.charAt(cursor);
            if (isQuote(character, profile)) {
                int end = quotedEnd(source, cursor, character, profile == Profile.SQL);
                Style style = profile == Profile.JSON && followedByColon(source, end)
                        ? Style.PROPERTY
                        : Style.STRING;
                spans.add(source.substring(cursor, end), style);
                cursor = end;
                continue;
            }
            if (profile == Profile.SHELL && character == '$') {
                int end = variableEnd(source, cursor);
                spans.add(source.substring(cursor, end), Style.VARIABLE);
                cursor = end;
                continue;
            }
            if (Character.isDigit(character)
                    || (character == '-' && cursor + 1 < source.length()
                    && Character.isDigit(source.charAt(cursor + 1)))) {
                int end = numberEnd(source, cursor);
                spans.add(source.substring(cursor, end), Style.NUMBER);
                cursor = end;
                continue;
            }
            if (Character.isJavaIdentifierStart(character)) {
                int end = cursor + 1;
                while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String word = source.substring(cursor, end);
                spans.add(word, profile.keywords.contains(word.toLowerCase(Locale.ROOT))
                        ? Style.KEYWORD
                        : Style.PLAIN);
                cursor = end;
                continue;
            }
            if (isOperator(character)) {
                spans.add(Character.toString(character), Style.OPERATOR);
                cursor++;
                continue;
            }
            int end = cursor + 1;
            while (end < source.length() && samePlainCategory(source.charAt(end), profile)) {
                end++;
            }
            spans.add(source.substring(cursor, end), Style.PLAIN);
            cursor = end;
        }
        return spans.result();
    }

    private static int commentEnd(String source, int cursor, Profile profile) {
        if (profile == Profile.SHELL && source.charAt(cursor) == '#') {
            return source.length();
        }
        if (profile == Profile.SQL && source.startsWith("--", cursor)) {
            return source.length();
        }
        if (profile == Profile.JAVA && source.startsWith("//", cursor)) {
            return source.length();
        }
        if ((profile == Profile.JAVA || profile == Profile.SQL)
                && source.startsWith("/*", cursor)) {
            int close = source.indexOf("*/", cursor + 2);
            return close < 0 ? source.length() : close + 2;
        }
        return -1;
    }

    private static boolean isQuote(char character, Profile profile) {
        return character == '"'
                || character == '\''
                || (profile == Profile.SHELL && character == '`');
    }

    private static int quotedEnd(String source, int start, char quote, boolean doubledQuote) {
        int cursor = start + 1;
        while (cursor < source.length()) {
            char character = source.charAt(cursor++);
            if (character == '\\' && cursor < source.length() && quote != '\'') {
                cursor++;
                continue;
            }
            if (character == quote) {
                if (doubledQuote && cursor < source.length() && source.charAt(cursor) == quote) {
                    cursor++;
                    continue;
                }
                return cursor;
            }
        }
        return source.length();
    }

    private static boolean followedByColon(String source, int cursor) {
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor < source.length() && source.charAt(cursor) == ':';
    }

    private static int variableEnd(String source, int start) {
        int cursor = start + 1;
        if (cursor < source.length() && source.charAt(cursor) == '{') {
            int close = source.indexOf('}', cursor + 1);
            return close < 0 ? source.length() : close + 1;
        }
        while (cursor < source.length()
                && (Character.isJavaIdentifierPart(source.charAt(cursor))
                || Character.isDigit(source.charAt(cursor)))) {
            cursor++;
        }
        return cursor;
    }

    private static int numberEnd(String source, int start) {
        int cursor = start + 1;
        while (cursor < source.length()) {
            char character = source.charAt(cursor);
            if (!(Character.isLetterOrDigit(character)
                    || character == '.' || character == '_' || character == '+' || character == '-')) {
                break;
            }
            cursor++;
        }
        return cursor;
    }

    private static boolean samePlainCategory(char character, Profile profile) {
        return !Character.isJavaIdentifierStart(character)
                && !Character.isDigit(character)
                && !isQuote(character, profile)
                && character != '$'
                && !isOperator(character)
                && character != '#';
    }

    private static boolean isOperator(char character) {
        return "{}[]():,.;=+-*/%<>!&|?".indexOf(character) >= 0;
    }

    private static int nextSpace(String source, int start) {
        int result = source.indexOf(' ', start);
        return result < 0 ? source.length() : result;
    }

    private static String normalized(String language) {
        return language.strip().toLowerCase(Locale.ROOT);
    }

    private static List<Span> plain(String source) {
        return source.isEmpty() ? List.of() : List.of(new Span(source, Style.PLAIN));
    }

    private enum Profile {
        JSON(JSON_KEYWORDS),
        SQL(SQL_KEYWORDS),
        JAVA(JAVA_KEYWORDS),
        SHELL(SHELL_KEYWORDS);

        private final Set<String> keywords;

        Profile(Set<String> keywords) {
            this.keywords = keywords;
        }
    }

    private static final class SpanBuilder {

        private final List<Span> spans = new ArrayList<>();

        void add(String text, Style style) {
            if (text.isEmpty()) {
                return;
            }
            if (!spans.isEmpty() && spans.getLast().style() == style) {
                Span previous = spans.removeLast();
                spans.add(new Span(previous.text() + text, style));
                return;
            }
            spans.add(new Span(text, style));
        }

        List<Span> result() {
            return List.copyOf(spans);
        }
    }
}
