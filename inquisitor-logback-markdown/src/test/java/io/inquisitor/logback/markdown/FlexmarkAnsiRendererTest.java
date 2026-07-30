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
                    "Scenario " + index + "\n\n• step " + index, results.get(index)));
        }
    }

    private static String stripAnsi(String value) {
        return ANSI.matcher(value).replaceAll("");
    }
}
