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

package io.inquisitor.logback.markdown.autoconfigure;

import lombok.val;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Installs the exclusive Markdown console immediately after Spring Boot configures
 * its logging system, before application startup messages are emitted.
 */
public final class MarkdownLoggingApplicationListener
        implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private final LoggingSystemProvider loggingSystemProvider;

    /** Creates the listener used by Spring Boot's factories loader. */
    public MarkdownLoggingApplicationListener() {
        this(LoggerFactory::getILoggerFactory);
    }

    MarkdownLoggingApplicationListener(LoggingSystemProvider loggingSystemProvider) {
        this.loggingSystemProvider = loggingSystemProvider;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        val environment = event.getEnvironment();
        if (!MarkdownConsoleSettings.enabled(environment)
                || MarkdownConsoleSettings.consoleMode(environment)
                != MarkdownConsoleMode.EXCLUSIVE) {
            return;
        }
        val palette = MarkdownConsoleSettings.palette(environment);
        new LogbackMarkdownInstaller(
                MarkdownConsoleSettings.renderer(palette, environment),
                MarkdownConsoleMode.EXCLUSIVE,
                MarkdownConsoleSettings.ansiPolicy(),
                palette)
                .install(loggingSystemProvider.get());
    }

    @Override
    public int getOrder() {
        return LoggingApplicationListener.DEFAULT_ORDER + 1;
    }
}
