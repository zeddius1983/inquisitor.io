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

package io.inquisitor.harness.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import io.inquisitor.harness.model.Scenario;
import io.inquisitor.harness.model.Step;
import lombok.val;
import org.jspecify.annotations.Nullable;

/**
 * Parses a markdown document into a {@link Scenario}.
 *
 * <p>Structure:
 * <ul>
 *   <li>the first level-1 heading ({@code # }) is the scenario name;</li>
 *   <li>level-2 headings ({@code ## }) delimit {@link Step steps}, their text
 *       being the step title and the body up to the next {@code ## } the
 *       instruction;</li>
 *   <li>text between the name and the first step is the shared description;</li>
 *   <li>a document with no {@code ## } headings collapses to a single implicit
 *       step whose instruction is the whole body.</li>
 * </ul>
 *
 * <p>Thread-safe and reusable.
 */
public class ScenarioParser {

    private static final int H1 = 1;
    private static final int H2 = 2;

    private final Parser parser = Parser.builder().build();

    /** Parses markdown with no known source. */
    public Scenario parse(String markdown) {
        return parse(markdown, null);
    }

    /**
     * Parses markdown into a scenario.
     *
     * @param markdown the scenario document
     * @param source   where it came from (file name / URI), used for the fallback
     *                 name when the document has no {@code # } heading
     */
    public Scenario parse(String markdown, @Nullable String source) {
        val document = parser.parse(markdown);

        val headings = StreamSupport.stream(document.getChildren().spliterator(), false)
                .filter(Heading.class::isInstance)
                .map(Heading.class::cast)
                .toList();

        val name = headings.stream()
                .filter(h -> h.getLevel() == H1)
                .findFirst()
                .map(h -> h.getText().toString().trim())
                .filter(s -> !s.isBlank())
                .orElseGet(() -> deriveName(source));

        int bodyStart = headings.stream()
                .filter(h -> h.getLevel() == H1)
                .findFirst()
                .map(Node::getEndOffset)
                .orElse(0);

        val stepHeadings = headings.stream().filter(h -> h.getLevel() == H2).toList();
        int length = markdown.length();

        if (stepHeadings.isEmpty()) {
            val instruction = markdown.substring(bodyStart, length).strip();
            return new Scenario(name, "", List.of(new Step(1, name, instruction)), source);
        }

        int firstStepStart = stepHeadings.getFirst().getStartOffset();
        val description = markdown.substring(Math.min(bodyStart, firstStepStart), firstStepStart).strip();

        val steps = new ArrayList<Step>();
        for (int i = 0; i < stepHeadings.size(); i++) {
            val heading = stepHeadings.get(i);
            int from = heading.getEndOffset();
            int to = (i + 1 < stepHeadings.size()) ? stepHeadings.get(i + 1).getStartOffset() : length;
            val title = heading.getText().toString().trim();
            val instruction = markdown.substring(from, to).strip();
            steps.add(new Step(i + 1, title, instruction));
        }

        return new Scenario(name, description, steps, source);
    }

    private static String deriveName(@Nullable String source) {
        if (source == null || source.isBlank()) {
            return "scenario";
        }
        var name = source;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - ".md".length());
        }
        return name.isBlank() ? "scenario" : name;
    }
}
