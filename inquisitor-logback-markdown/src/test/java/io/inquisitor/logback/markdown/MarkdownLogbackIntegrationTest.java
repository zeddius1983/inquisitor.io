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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownLogbackIntegrationTest {

    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");

    @TempDir
    Path tempDir;

    @Test
    void bundledRuleRegistersMdMsgWithoutChangingMsg() throws Exception {
        Path logFile = tempDir.resolve("integration.log");
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putProperty("TEST_LOG", logFile.toString());

        URL configuration = getClass().getResource("/logback-markdown-integration.xml");
        assertNotNull(configuration);
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(configuration);
        context.start();

        Logger logger = context.getLogger("integration");
        logger.info(MarkdownMarkers.markdown(), "# Integrated **Markdown**");
        context.stop();

        String output = ANSI.matcher(Files.readString(logFile)).replaceAll("")
                .replace("\r\n", "\n");
        assertEquals("Integrated Markdown\n|RAW:# Integrated **Markdown**\n", output);
    }
}
