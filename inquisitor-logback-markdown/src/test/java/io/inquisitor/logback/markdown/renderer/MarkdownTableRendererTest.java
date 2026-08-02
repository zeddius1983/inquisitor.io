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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class MarkdownTableRendererTest {

    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");

    private final FlexmarkAnsiRenderer renderer = new FlexmarkAnsiRenderer(MarkdownRendererOptions.plain());

    @Test
    void rendersBasicTableWithUnicodeBordersAndAlignment() {
        String rendered = renderer.render("""
                | Model | Groundedness | Notes |
                |:------|-------------:|:------|
                | gemma-3 | **100%** | Fast |
                | qwen-3 | 92% | Uses `reasoning` |
                """);

        assertEquals("""
                ┌─────────┬──────────────┬────────────────┐
                │ Model   │ Groundedness │ Notes          │
                ├─────────┼──────────────┼────────────────┤
                │ gemma-3 │         100% │ Fast           │
                │ qwen-3  │          92% │ Uses reasoning │
                └─────────┴──────────────┴────────────────┘""", rendered);
    }

    @Test
    void parsesTableWithoutOuterPipes() {
        String rendered = renderer.render("""
                Left | Centre | Right
                :--- | :----: | ----:
                a | b | c
                aa | bb | cc
                """);

        assertEquals("""
                ┌──────┬────────┬───────┐
                │ Left │ Centre │ Right │
                ├──────┼────────┼───────┤
                │ a    │   b    │     c │
                │ aa   │   bb   │    cc │
                └──────┴────────┴───────┘""", rendered);
    }

    @Test
    void preservesEmptyEscapedMissingAndExtraCells() {
        String rendered = renderer.render("""
                | A | B |
                |---|---|
                | | escaped \\| pipe |
                | only |
                | one | two | extra |
                """);

        assertEquals("""
                ┌──────┬────────────────┬───────┐
                │ A    │ B              │       │
                ├──────┼────────────────┼───────┤
                │      │ escaped | pipe │       │
                │ only │                │       │
                │ one  │ two            │ extra │
                └──────┴────────────────┴───────┘""", rendered);
    }

    @Test
    void inlineStylesDoNotChangeAnsiGeometry() {
        String markdown = """
                | Kind | Content |
                |---|---|
                | styled | **bold** *emphasis* `code` [docs](https://example.test) |
                """;
        String plain = renderer.render(markdown);
        FlexmarkAnsiRenderer ansiRenderer = new FlexmarkAnsiRenderer(MarkdownRendererOptions.ansi());
        String ansi = ansiRenderer.render(markdown);
        String followedByPlainText = ansiRenderer.render(markdown + "\nAfter table");

        assertEquals(plain, stripAnsi(ansi));
        assertTrue(ansi.contains("38;2;102;92;84"));
        assertTrue(ansi.contains("38;2;104;157;106"));
        assertTrue(ansi.contains("38;2;215;153;33"));
        assertTrue(ansi.contains("38;2;177;98;134"));
        assertTrue(ansi.contains("38;2;69;133;136"));
        assertFalse(followedByPlainText.substring(
                followedByPlainText.indexOf("After table")).contains("\u001B["));
    }

    @Test
    void wrapsWordsAndLongTokensWithinConfiguredWidth() {
        String rendered = new FlexmarkAnsiRenderer(
                MarkdownRendererOptions.plain().withTableWidth(24)).render("""
                | Key | Value |
                |---|---|
                | notes | alpha beta gamma delta |
                | token | abcdefghijklmnop |
                """);

        assertEquals("""
                ┌───────┬──────────────┐
                │ Key   │ Value        │
                ├───────┼──────────────┤
                │ notes │ alpha beta   │
                │       │ gamma delta  │
                │ token │ abcdefghijkl │
                │       │ mnop         │
                └───────┴──────────────┘""", rendered);
        assertTrue(rendered.lines().allMatch(line -> UnicodeDisplayWidth.INSTANCE.width(line) <= 24));
    }

    @Test
    void overflowsReadablyWhenColumnBordersAloneExceedTheLimit() {
        String rendered = new FlexmarkAnsiRenderer(
                MarkdownRendererOptions.plain().withTableWidth(20)).render("""
                | A | B | C | D | E | F | G |
                |---|---|---|---|---|---|---|
                | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
                """);

        assertTrue(rendered.contains("│ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │"));
        assertTrue(rendered.lines()
                .mapToInt(UnicodeDisplayWidth.INSTANCE::width)
                .allMatch(width -> width == 29));
    }

    @Test
    void alignsAsciiCombiningCjkAndEmojiByVisibleWidth() {
        String rendered = renderer.render("""
                | Kind | Value |
                |---|---:|
                | ASCII | abc |
                | Combining | é |
                | CJK | 模型 |
                | Emoji | 🙂 |
                """);
        List<Integer> widths = rendered.lines()
                .map(UnicodeDisplayWidth.INSTANCE::width)
                .toList();

        assertTrue(widths.stream().allMatch(widths.getFirst()::equals));
        assertTrue(rendered.contains("│ Combining │     é │"));
        assertTrue(rendered.contains("│ CJK       │  模型 │"));
        assertTrue(rendered.contains("│ Emoji     │    🙂 │"));
    }

    @Test
    void tablesRetainBlockQuoteAndListPrefixes() {
        String quoted = renderer.render("""
                > | A | B |
                > |---|---|
                > | x | y |
                """);
        String listed = renderer.render("""
                - Results:

                  | A | B |
                  |---|---|
                  | x | y |
                """);

        assertTrue(quoted.lines().allMatch(line -> line.startsWith("│ ")));
        assertTrue(quoted.contains("│ │ x │ y │"));
        assertTrue(listed.contains(" • Results:\n   ┌───┬───┐"));
        assertTrue(listed.contains("\n   │ x │ y │\n"));
    }

    @Test
    void activePrefixesAreDeductedFromTheConfiguredTableWidth() {
        FlexmarkAnsiRenderer narrow = new FlexmarkAnsiRenderer(
                MarkdownRendererOptions.plain().withTableWidth(24));
        String quoted = narrow.render("""
                > | Key | Value |
                > |---|---|
                > | note | alpha beta gamma delta |
                """);
        String listed = narrow.render("""
                - Results:

                  | Key | Value |
                  |---|---|
                  | note | alpha beta gamma delta |
                """);

        assertTrue(quoted.lines().allMatch(line ->
                UnicodeDisplayWidth.INSTANCE.width(line) <= 24));
        assertTrue(listed.lines().allMatch(line ->
                UnicodeDisplayWidth.INSTANCE.width(line) <= 24));
        assertTrue(quoted.contains("alpha"));
        assertTrue(quoted.contains("delta"));
        assertTrue(listed.contains("alpha"));
        assertTrue(listed.contains("delta"));
    }

    @Test
    void concurrentTableRendersDoNotShareLayoutOrStyleState() throws Exception {
        FlexmarkAnsiRenderer shared = new FlexmarkAnsiRenderer(MarkdownRendererOptions.ansi());
        List<Callable<String>> calls = IntStream.range(0, 100)
                .mapToObj(index -> (Callable<String>) () -> shared.render("""
                        | Index | Value |
                        |---:|---|
                        | %d | **row-%d** |
                        """.formatted(index, index)))
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<String> results = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        }
                        catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            for (int index = 0; index < results.size(); index++) {
                String plain = stripAnsi(results.get(index));
                assertTrue(plain.contains("│ row-" + index + " │"));
                assertFalse(results.get(index).endsWith("\u001B["));
            }
        }
    }

    private static String stripAnsi(String value) {
        return ANSI.matcher(value).replaceAll("");
    }
}
