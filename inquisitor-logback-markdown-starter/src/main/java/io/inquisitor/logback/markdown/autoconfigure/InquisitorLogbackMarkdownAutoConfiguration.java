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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;
import io.inquisitor.logback.markdown.converter.MarkdownMessageConverter;
import io.inquisitor.logback.markdown.renderer.MarkdownRenderer;
import lombok.val;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Installs marker-aware Markdown conversion into compatible Logback console
 * layouts after Spring Boot has initialized logging.
 */
@AutoConfiguration
@ConditionalOnClass({LoggerContext.class, ConsoleAppender.class, MarkdownMessageConverter.class})
@ConditionalOnProperty(
        prefix = "inquisitor.logging.markdown",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MarkdownLoggingProperties.class)
public class InquisitorLogbackMarkdownAutoConfiguration {

    /** Creates the Markdown logging autoconfiguration. */
    public InquisitorLogbackMarkdownAutoConfiguration() {
    }

    @Bean
    @ConditionalOnMissingBean
    MarkdownRenderer inquisitorLogbackMarkdownRenderer(
            MarkdownLoggingProperties properties,
            Environment environment) {
        return MarkdownConsoleSettings.renderer(
                MarkdownConsoleSettings.paletteOrDefault(properties.palette()), environment);
    }

    @Bean
    @ConditionalOnMissingBean
    LoggingSystemProvider inquisitorLoggingSystemProvider() {
        return LoggerFactory::getILoggerFactory;
    }

    @Bean
    LogbackMarkdownInstaller inquisitorLogbackMarkdownInstaller(
            MarkdownRenderer renderer,
            MarkdownLoggingProperties properties,
            Environment environment) {
        val consoleMode = MarkdownConsoleSettings.consoleMode(
                properties.consoleMode(), environment);
        val palette = MarkdownConsoleSettings.paletteOrDefault(properties.palette());
        return new LogbackMarkdownInstaller(
                renderer, consoleMode, MarkdownConsoleSettings.ansiPolicy(),
                palette);
    }

    @Bean
    SmartInitializingSingleton inquisitorLogbackMarkdownInstallation(
            LogbackMarkdownInstaller installer,
            LoggingSystemProvider loggingSystemProvider) {
        return () -> installer.install(loggingSystemProvider.get());
    }

}
