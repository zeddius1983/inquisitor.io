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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import io.inquisitor.logback.markdown.highlight.SyntaxHighlighter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FlexmarkAnsiRendererTest {

    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");

    private final FlexmarkAnsiRenderer plainRenderer = new FlexmarkAnsiRenderer(false);

    @Test
    void rendersSupportedBlockAndInlineNodes() {
        String markdown = """
                # Scenario heading

                Paragraph with **strong**, *emphasis*, `inline code`, and [docs](https://example.test/docs).

                > quoted
                > continuation

                - first
                  3) nested
                - second

                ```sql
                **not emphasis**
                ```

                    indented `code`
                """;

        assertEquals("""
                Scenario heading

                Paragraph with strong, emphasis, inline code, and docs (https://example.test/docs).

                │ quoted
                │ continuation

                 • first
                   3) nested
                 • second

                **not emphasis**

                indented `code`""", plainRenderer.render(markdown));
    }

    @Test
    void preservesParagraphAndNestedListWhitespace() {
        String rendered = plainRenderer.render("""
                Before.

                - parent
                  - child
                - sibling

                After.
                """);

        assertEquals("""
                Before.

                 • parent
                   • child
                 • sibling

                After.""", rendered);
    }

    @Test
    void fencedCodeDoesNotInterpretMarkdownLookingContent() {
        String rendered = plainRenderer.render("""
                ```markdown
                # not a heading
                **not strong** and `not inline code`
                ```
                """);

        assertEquals("# not a heading\n**not strong** and `not inline code`", rendered);
    }

    @Test
    void ansiCodeBlocksUseAnEqualWidthBackgroundPanelAndSyntaxColors() {
        FlexmarkAnsiRenderer renderer = new FlexmarkAnsiRenderer(true);

        String rendered = renderer.render("""
                ```json
                {"a":1}

                {}
                ```
                """);
        String plain = stripAnsi(rendered);

        assertTrue(rendered.contains("48;2;60;56;54"));
        assertTrue(rendered.contains("\u001B["));
        assertEquals(" {\"a\":1} \n         \n {}      ", plain);
        assertTrue(plain.lines().mapToInt(String::length).distinct().count() == 1);
    }

    @Test
    void customHighlighterReceivesFenceLanguageAndCannotCorruptCode() {
        AtomicReference<String> language = new AtomicReference<>();
        SyntaxHighlighter corrupting = (receivedLanguage, source) -> {
            language.set(receivedLanguage);
            return List.of(new SyntaxHighlighter.Span("different", SyntaxHighlighter.Style.KEYWORD));
        };
        FlexmarkAnsiRenderer renderer = new FlexmarkAnsiRenderer(true, corrupting);

        String rendered = renderer.render("```custom option\noriginal\n```");

        assertEquals("custom", language.get());
        assertEquals(" original ", stripAnsi(rendered));
    }

    @Test
    void failingCustomHighlighterFallsBackToUnchangedCode() {
        SyntaxHighlighter failing = (language, source) -> {
            throw new IllegalStateException("broken grammar");
        };

        String rendered = new FlexmarkAnsiRenderer(true, failing)
                .render("```custom\nsource\n```");

        assertEquals(" source ", stripAnsi(rendered));
    }

    @Test
    void preservesLeafContentAndUnescapesMarkdownText() {
        String rendered = plainRenderer.render("""
                see <https://example.test/x> and <dev@example.test> now

                AT&amp;T and &lt;tag&gt;

                a \\*not emphasis\\* b

                before <span>inside</span> after

                ---

                ![diagram](https://img.test/a.png)
                """);

        assertEquals("""
                see https://example.test/x and dev@example.test now

                AT&T and <tag>

                a *not emphasis* b

                before <span>inside</span> after

                ────────

                diagram (https://img.test/a.png)""", rendered);
    }

    @Test
    void rendersHardBreaksAndDoesNotRepeatSelfLabelledLinkUrls() {
        String rendered = plainRenderer.render(
                "first line  \nsecond line with [https://example.test](https://example.test)\n");

        assertEquals("""
                first line
                second line with https://example.test""", rendered);
    }

    @Test
    void preservesUnresolvedReferenceSyntaxInLogContent() {
        assertEquals("response {\"ids\":[\"a\",\"b\"]}",
                plainRenderer.render("response {\"ids\":[\"a\",\"b\"]}"));
        assertEquals("array [1,2,3] end", plainRenderer.render("array [1,2,3] end"));
        assertEquals("log [main] INFO started",
                plainRenderer.render("log [main] INFO started"));
        assertEquals("see [ERROR] here", plainRenderer.render("see [ERROR] here"));
        assertEquals(" • [ ] todo", plainRenderer.render("- [ ] todo"));
    }

    @Test
    void rendersReferenceLinksAndHidesTheirDefinitions() {
        String rendered = plainRenderer.render("[text][ref]\n\n[ref]: https://e.test");

        assertEquals("text (https://e.test)", rendered);
    }

    @Test
    void alignsListContinuationsWithContentAfterTheActualMarkerWidth() {
        String singleDigit = plainRenderer.render("""
                1. one
                2. two
                   more text
                3. three
                """);
        String doubleDigit = plainRenderer.render("""
                10. ten
                    continuation
                    - nested
                      nested continuation
                """);

        assertEquals(" 1. one\n 2. two\n    more text\n 3. three", singleDigit);
        assertEquals(" 10. ten\n     continuation\n     • nested\n       nested continuation",
                doubleDigit);
    }

    @Test
    void prefixesBlankLinesInsideBlockQuotes() {
        String rendered = plainRenderer.render("""
                > first paragraph
                >
                > second paragraph
                """);

        assertEquals("""
                │ first paragraph
                │
                │ second paragraph""", rendered);
    }

    @Test
    void disabledAnsiProducesReadablePlainText() {
        String rendered = plainRenderer.render("# Heading with **strong** text");

        assertEquals("Heading with strong text", rendered);
        assertFalse(rendered.contains("\u001B["));
    }

    @Test
    void rendersPowerlineHeadingsAsBackgroundColoredPills() {
        String markdown = "###  actual-model  Accounts  Verify balances  ▰▰▰▰▰ 4/4  Running ";

        String rendered = new FlexmarkAnsiRenderer(true).render(markdown);

        assertEquals(" actual-model  Accounts  Verify balances  ▰▰▰▰▰ 4/4  Running ",
                stripAnsi(rendered));
        assertTrue(rendered.contains("48;2;214;93;14"));
        assertTrue(rendered.contains("48;2;215;153;33"));
        assertTrue(rendered.contains("48;2;104;157;106"));
        assertTrue(rendered.contains("48;2;152;151;26"));
        assertTrue(rendered.substring(0, rendered.indexOf(" actual-model "))
                .contains("38;2;214;93;14"));
        assertTrue(rendered.substring(rendered.indexOf(" Running ") + " Running ".length())
                .contains("38;2;152;151;26"));
    }

    @Test
    void leavesPowerlineHeadingsReadableWhenAnsiIsDisabled() {
        String rendered = plainRenderer.render(
                "###  Accounts  Verify balances  ▰▰▰▰▰ 4/4  Running ");

        assertEquals(" Accounts  Verify balances  ▰▰▰▰▰ 4/4  Running ",
                rendered);
    }

    @Test
    void rendersFailedPowerlineStatusWithFailureBackground() {
        String rendered = new FlexmarkAnsiRenderer(true).render(
                "###  Accounts  Verify balances  ▰▰▰▰▰ 4/4  FAIL  ⧖ 13.289 s ");

        assertEquals(" Accounts  Verify balances  ▰▰▰▰▰ 4/4  FAIL  ⧖ 13.289 s ",
                stripAnsi(rendered));
        assertTrue(rendered.contains("48;2;204;36;29"));
        assertTrue(rendered.contains("48;2;102;92;84"));
    }

    @Test
    void rendersHttpBreadcrumbTargetAndStatusAsDistinctSegments() {
        String rendered = new FlexmarkAnsiRenderer(true).render(
                "###  HTTP  localhost  POST  /accounts/import  HTTP 201 ");

        assertEquals(" HTTP  localhost  POST  /accounts/import  HTTP 201 ",
                stripAnsi(rendered));
        assertTrue(rendered.contains("48;2;102;92;84"));
        assertTrue(rendered.contains("48;2;152;151;26"));
    }

    @Test
    void rendersSqlBreadcrumbWithSemanticExecutionStatus() {
        String rendered = new FlexmarkAnsiRenderer(true).render(
                "###  SQL  default  EXECUTE ");

        assertEquals(" SQL  default  EXECUTE ", stripAnsi(rendered));
        assertTrue(rendered.contains("48;2;152;151;26"));
    }

    @ParameterizedTest
    @CsvSource({
            "RUN, '48;2;152;151;26'",
            "EVALUATION, '48;2;152;151;26'",
            "GROUNDED, '48;2;152;151;26'",
            "PARTIALLY_GROUNDED, '48;2;215;153;33'",
            "NOT_EVALUATED, '48;2;215;153;33'",
            "UNSUPPORTED, '48;2;204;36;29'",
            "CONTRADICTED, '48;2;204;36;29'"
    })
    void rendersLifecycleAndEvaluationStatusesWithSemanticBackgrounds(
            String status,
            String backgroundCode) {
        String rendered = new FlexmarkAnsiRenderer(true).render(
                "###  actual-model  Accounts  Verify balances  ▰▰▰▰▰ 4/4  "
                        + status + " ");

        assertTrue(rendered.contains(backgroundCode));
        assertFalse(rendered.contains("48;2;60;56;54"));
    }

    @Test
    void resetsStylesBeforeAdjacentPlainTextAndBetweenMessages() {
        FlexmarkAnsiRenderer renderer = new FlexmarkAnsiRenderer(true);

        String styled = renderer.render("**strong** plain");
        String plain = renderer.render("plain");

        assertEquals("strong plain", stripAnsi(styled));
        assertTrue(styled.substring(styled.indexOf("strong") + "strong".length(),
                styled.indexOf(" plain")).contains("\u001B["));
        assertFalse(plain.contains("\u001B["));
    }

    @Test
    void oneRendererCanServeConcurrentCalls() throws Exception {
        FlexmarkAnsiRenderer renderer = new FlexmarkAnsiRenderer(false);
        List<Callable<String>> calls = IntStream.range(0, 100)
                .mapToObj(index -> (Callable<String>) () -> renderer.render(
                        "# Scenario " + index + "\n\n- step `" + index + "`"))
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

            IntStream.range(0, results.size()).forEach(index -> assertEquals(
                    "Scenario " + index + "\n\n • step " + index, results.get(index)));
        }
    }

    private static String stripAnsi(String value) {
        return ANSI.matcher(value).replaceAll("");
    }
}
