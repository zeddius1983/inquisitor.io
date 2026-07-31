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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.LayoutBase;
import ch.qos.logback.core.encoder.EncoderBase;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import io.inquisitor.logback.markdown.FlexmarkAnsiRenderer;
import io.inquisitor.logback.markdown.MarkdownMarkers;
import io.inquisitor.logback.markdown.MarkdownRenderer;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InquisitorLogbackMarkdownAutoConfigurationTest {

    private final List<LoggerContext> loggerContexts = new ArrayList<>();
    private final AnsiOutput.Enabled originalAnsiPolicy = AnsiOutput.getEnabled();

    @AfterEach
    void restoreGlobalState() {
        loggerContexts.forEach(LoggerContext::stop);
        AnsiOutput.setEnabled(originalAnsiPolicy);
    }

    @Test
    void installsIntoAConsolePatternWithoutChangingTheSurroundingPattern() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "prefix [%level] %msg");

        runner(loggerContext).run(context -> {
            assertThat(context).hasSingleBean(MarkdownLoggingProperties.class);
            assertThat(context.getBean(MarkdownLoggingProperties.class).enabled()).isTrue();
            assertThat(context).hasSingleBean(MarkdownRenderer.class);
            assertThat(context).hasBean("inquisitorLogbackMarkdownInstallation");

            assertThat(console.layout().doLayout(marked("## rendered")))
                    .isEqualTo("prefix [INFO] rendered");
            assertThat(console.layout().doLayout(event("## ordinary")))
                    .isEqualTo("prefix [INFO] ## ordinary");
        });
    }

    @Test
    void backsOffWhenDisabled() {
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "%msg");

        runner(loggerContext)
                .withPropertyValues("inquisitor.logging.markdown.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MarkdownLoggingProperties.class);
                    assertThat(context).doesNotHaveBean(MarkdownRenderer.class);
                    assertThat(console.layout().doLayout(marked("## raw"))).isEqualTo("## raw");
                });
    }

    @Test
    void bootAlwaysAndNeverPoliciesControlAnsiDeterministically() {
        assertAnsiPolicy(AnsiOutput.Enabled.ALWAYS, true);
        assertAnsiPolicy(AnsiOutput.Enabled.NEVER, false);
    }

    @Test
    void leavesFileLayoutsRaw() {
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "%msg");
        val fileLayout = file(loggerContext, "FILE", "%msg");
        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));

        assertThat(installer.install(loggerContext)).isEqualTo(1);
        assertThat(console.layout().doLayout(marked("## rendered"))).isEqualTo("rendered");
        assertThat(fileLayout.doLayout(marked("## raw"))).isEqualTo("## raw");
    }

    @Test
    void skipsStructuredAndUnsupportedConsoleEncoders() {
        val loggerContext = loggerContext();
        val structured = new ConsoleAppender<ILoggingEvent>();
        structured.setContext(loggerContext);
        structured.setName("JSON_CONSOLE");
        structured.setEncoder(new JsonLikeEncoder());
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(structured);

        val customLayout = new PlainLayout();
        customLayout.setContext(loggerContext);
        customLayout.start();
        val wrappingEncoder = new LayoutWrappingEncoder<ILoggingEvent>();
        wrappingEncoder.setContext(loggerContext);
        wrappingEncoder.setLayout(customLayout);
        val custom = new ConsoleAppender<ILoggingEvent>();
        custom.setContext(loggerContext);
        custom.setName("CUSTOM_CONSOLE");
        custom.setEncoder(wrappingEncoder);
        loggerContext.getLogger("custom").addAppender(custom);

        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));

        assertThat(installer.install(loggerContext)).isZero();
        assertThat(customLayout.doLayout(marked("## raw"))).isEqualTo("## raw");
    }

    @Test
    void installationIsIdempotentAndDeduplicatesSharedAppenders() {
        val loggerContext = loggerContext();
        val console = console(loggerContext, "SHARED", "%message");
        loggerContext.getLogger("named").addAppender(console.appender());
        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));

        assertThat(installer.install(loggerContext)).isEqualTo(1);
        assertThat(installer.install(loggerContext)).isZero();
        assertThat(console.layout().doLayout(marked("## once"))).isEqualTo("once");
    }

    @Test
    void reachesConsoleAppendersBehindCompositeAppenders() {
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "%msg");
        val root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAppender(console.appender());
        val async = new AsyncAppender();
        async.setContext(loggerContext);
        async.setName("ASYNC");
        async.addAppender(console.appender());
        root.addAppender(async);
        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));

        assertThat(installer.install(loggerContext)).isEqualTo(1);
        assertThat(console.layout().doLayout(marked("## rendered"))).isEqualTo("rendered");
    }

    @Test
    void recompilesAStartedLayoutWithoutStoppingIt() {
        val loggerContext = loggerContext();
        val layout = new LifecycleObservingPatternLayout();
        layout.setContext(loggerContext);
        layout.setPattern("%msg");
        layout.start();
        val encoder = new LayoutWrappingEncoder<ILoggingEvent>();
        encoder.setContext(loggerContext);
        encoder.setLayout(layout);
        val appender = new ConsoleAppender<ILoggingEvent>();
        appender.setContext(loggerContext);
        appender.setName("OBSERVED_CONSOLE");
        appender.setEncoder(encoder);
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));

        assertThat(installer.install(loggerContext)).isEqualTo(1);
        assertThat(layout.stopped).isFalse();
        assertThat(layout.recompiledWhileStarted).isTrue();
        assertThat(layout.doLayout(marked("## rendered"))).isEqualTo("rendered");
    }

    @Test
    void restoresTheOriginalLayoutWhenRecompilationFails() {
        val loggerContext = loggerContext();
        val layout = new FailOncePatternLayout();
        layout.setContext(loggerContext);
        layout.setPattern("%msg");
        layout.start();
        val encoder = new LayoutWrappingEncoder<ILoggingEvent>();
        encoder.setContext(loggerContext);
        encoder.setLayout(layout);
        val appender = new ConsoleAppender<ILoggingEvent>();
        appender.setContext(loggerContext);
        appender.setName("FAILING_CONSOLE");
        appender.setEncoder(encoder);
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));

        assertThat(installer.install(loggerContext)).isZero();
        assertThat(layout.isStarted()).isTrue();
        assertThat(layout.doLayout(marked("## raw"))).isEqualTo("## raw");
    }

    @Test
    void supportsEveryStandardMessageConversionWord() {
        val loggerContext = loggerContext();
        val shortWord = console(loggerContext, "SHORT", "%m");
        val normalWord = console(loggerContext, "NORMAL", "%msg");
        val longWord = console(loggerContext, "LONG", "%message");
        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));

        assertThat(installer.install(loggerContext)).isEqualTo(3);
        assertThat(shortWord.layout().doLayout(marked("## short"))).isEqualTo("short");
        assertThat(normalWord.layout().doLayout(marked("## normal"))).isEqualTo("normal");
        assertThat(longWord.layout().doLayout(marked("## long"))).isEqualTo("long");
    }

    @Test
    void skipsAnAlternativeSlf4jBackend() {
        val installer = new LogbackMarkdownInstaller(new FlexmarkAnsiRenderer(false));
        ILoggerFactory alternative = LoggerFactory::getLogger;

        assertThat(installer.install(alternative)).isZero();
    }

    @Test
    void backsOffWhenLogbackIsNotAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        InquisitorLogbackMarkdownAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader(LoggerContext.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MarkdownLoggingProperties.class);
                    assertThat(context).doesNotHaveBean(MarkdownRenderer.class);
                });
    }

    private void assertAnsiPolicy(AnsiOutput.Enabled policy, boolean expectedAnsi) {
        AnsiOutput.setEnabled(policy);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE-" + policy, "%msg");

        runner(loggerContext).run(ignored -> assertThat(
                console.layout().doLayout(marked("## rendered")).contains("\u001B["))
                .isEqualTo(expectedAnsi));
    }

    private ApplicationContextRunner runner(LoggerContext loggerContext) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        InquisitorLogbackMarkdownAutoConfiguration.class))
                .withBean(LoggingSystemProvider.class, () -> () -> loggerContext);
    }

    private LoggerContext loggerContext() {
        val context = new LoggerContext();
        context.start();
        loggerContexts.add(context);
        return context;
    }

    private static ConsoleFixture console(
            LoggerContext context, String name, String pattern) {
        val encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();
        val layout = (PatternLayout) encoder.getLayout();
        val appender = new ConsoleAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName(name);
        appender.setEncoder(encoder);
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        return new ConsoleFixture(layout, appender);
    }

    private static PatternLayout file(LoggerContext context, String name, String pattern) {
        val encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(pattern);
        encoder.start();
        val layout = (PatternLayout) encoder.getLayout();
        val appender = new FileAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName(name);
        appender.setEncoder(encoder);
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        return layout;
    }

    private static LoggingEvent marked(String message) {
        val event = event(message);
        event.addMarker(MarkdownMarkers.markdown());
        return event;
    }

    private static LoggingEvent event(String message) {
        val event = new LoggingEvent();
        event.setLoggerName("test");
        event.setLevel(Level.INFO);
        event.setMessage(message);
        return event;
    }

    private record ConsoleFixture(
            PatternLayout layout,
            ConsoleAppender<ILoggingEvent> appender) {
    }

    private static final class JsonLikeEncoder extends EncoderBase<ILoggingEvent> {

        @Override
        public byte[] headerBytes() {
            return new byte[0];
        }

        @Override
        public byte[] encode(ILoggingEvent event) {
            return "{}".getBytes(UTF_8);
        }

        @Override
        public byte[] footerBytes() {
            return new byte[0];
        }
    }

    private static final class PlainLayout extends LayoutBase<ILoggingEvent> {

        @Override
        public String doLayout(ILoggingEvent event) {
            return event.getFormattedMessage();
        }
    }

    private static final class FailOncePatternLayout extends PatternLayout {

        private int starts;

        @Override
        public void start() {
            starts++;
            if (starts == 2) {
                throw new IllegalStateException("simulated recompile failure");
            }
            super.start();
        }
    }

    private static final class LifecycleObservingPatternLayout extends PatternLayout {

        private boolean stopped;
        private boolean recompiledWhileStarted;

        @Override
        public void start() {
            if (isStarted()) {
                recompiledWhileStarted = true;
            }
            super.start();
        }

        @Override
        public void stop() {
            stopped = true;
            super.stop();
        }
    }
}
