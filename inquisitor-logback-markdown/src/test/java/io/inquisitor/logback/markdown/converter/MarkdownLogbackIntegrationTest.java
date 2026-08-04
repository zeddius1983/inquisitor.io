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

package io.inquisitor.logback.markdown.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import io.inquisitor.logback.markdown.marker.MarkdownMarkers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownLogbackIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void bundledRuleRegistersMdMsgWithoutChangingMsg() throws Exception {
        Path logFile = tempDir.resolve("integration.log");
        String output = log(logFile, "# Integrated **Markdown**");

        assertEquals(-1, output.indexOf('\u001B'));
        assertEquals("    Integrated Markdown\n|RAW:# Integrated **Markdown**\n", output);
    }

    @Test
    void bundledRuleRendersTablesWhileMsgKeepsTheRawMarkdown() throws Exception {
        Path logFile = tempDir.resolve("table.log");
        String markdown = """
                | A | B |
                |---|---:|
                | x | 2 |""";

        String output = log(logFile, markdown);

        assertEquals(-1, output.indexOf('\u001B'));
        assertEquals("""
                    ┌───┬───┐
                    │ A │ B │
                    ├───┼───┤
                    │ x │ 2 │
                    └───┴───┘
                |RAW:| A | B |
                |---|---:|
                | x | 2 |
                """, output);
    }

    @Test
    void bundledRuleRegistersMarkerOnlyFullEventConversion() throws Exception {
        Path logFile = tempDir.resolve("event.log");
        String output = logEvent(logFile, "## Integrated event");

        assertEquals(-1, output.indexOf('\u001B'));
        assertTrue(output.matches(
                "\\n\\d{2}:\\d{2}:\\d{2}\\.\\d{3}INFO\\n"
                        + "\\n    Integrated event\\n"));
        assertFalse(output.contains("ordinary"));
    }

    private String log(Path logFile, String message) throws Exception {
        LoggerContext context = context(logFile, "/logback-markdown-integration.xml");

        Logger logger = context.getLogger("integration");
        logger.info(MarkdownMarkers.markdown(), message);
        context.stop();

        return Files.readString(logFile).replace("\r\n", "\n");
    }

    private String logEvent(Path logFile, String message) throws Exception {
        LoggerContext context = context(logFile, "/logback-markdown-event-integration.xml");

        Logger logger = context.getLogger("event-integration");
        logger.info("ordinary");
        logger.info(MarkdownMarkers.markdown(), message);
        context.stop();

        return Files.readString(logFile).replace("\r\n", "\n");
    }

    private LoggerContext context(Path logFile, String resource) throws Exception {
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putProperty("TEST_LOG", logFile.toString());

        URL configuration = getClass().getResource(resource);
        assertNotNull(configuration);
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(configuration);
        context.start();
        return context;
    }
}
