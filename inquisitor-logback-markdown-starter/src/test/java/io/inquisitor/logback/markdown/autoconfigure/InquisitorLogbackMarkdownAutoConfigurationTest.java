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
import ch.qos.logback.core.spi.FilterReply;
import io.inquisitor.logback.markdown.marker.MarkdownMarkerTurboFilter;
import io.inquisitor.logback.markdown.marker.MarkdownMarkers;
import io.inquisitor.logback.markdown.renderer.FlexmarkAnsiRenderer;
import io.inquisitor.logback.markdown.renderer.MarkdownRenderer;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

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
            assertThat(context.getBean(MarkdownLoggingProperties.class).consoleMode())
                    .isEqualTo(MarkdownConsoleMode.AUTO);
            assertThat(context.getBean(MarkdownLoggingProperties.class).palette())
                    .isEqualTo("gruvbox");
            assertThat(context).hasSingleBean(MarkdownRenderer.class);
            assertThat(context).hasBean("inquisitorLogbackMarkdownInstallation");

            assertThat(console.layout().doLayout(marked("## rendered")))
                    .isEqualTo("prefix [INFO]     rendered");
            assertThat(console.layout().doLayout(event("## ordinary")))
                    .isEqualTo("prefix [INFO] ## ordinary");
        });
    }

    @Test
    void configuredPaletteStylesSharedMarkdownRendering() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "%msg");

        runner(loggerContext)
                .withPropertyValues("inquisitor.logging.markdown.palette=tokyo-night")
                .run(context -> {
                    assertThat(context.getBean(MarkdownLoggingProperties.class).palette())
                            .isEqualTo("tokyo-night");
                    assertThat(console.layout().doLayout(marked("## rendered")))
                            .contains("38;2;125;207;255");
                });
    }

    @Test
    void harnessMarkdownFormatUsesAnExclusivePowerlineConsole() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "prefix [%level] %msg%n");
        val root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);

        runner(loggerContext)
                .withPropertyValues("inquisitor.harness.logging.format=markdown")
                .run(context -> {
                    val markedOutput = console.layout().doLayout(marked("## Fetch account"));
                    val lineSeparator = System.lineSeparator();

                    assertThat(markedOutput)
                            .matches("\\R\\d{2}:\\d{2}:\\d{2}\\.\\d{3}INFO\\R"
                                    + "\\R    Fetch account\\R")
                            .doesNotContain("prefix", "    ");
                    assertThat(console.layout().doLayout(event("ordinary"))).isEmpty();
                    assertThat(loggerContext.getTurboFilterList())
                            .singleElement()
                            .isInstanceOf(MarkdownMarkerTurboFilter.class);

                    val logger = loggerContext.getLogger("exclusive-test");
                    assertThat(logger.isDebugEnabled()).isFalse();
                    assertThat(logger.isDebugEnabled(MarkdownMarkers.markdown())).isTrue();
                    assertThat(loggerContext.getTurboFilterList().getFirst().decide(
                            null, logger, Level.DEBUG, "ordinary", null, null))
                            .isEqualTo(FilterReply.NEUTRAL);
                    assertThat(markedOutput).endsWith(lineSeparator);
                });
    }

    @Test
    void earlyListenerSuppressesSpringStartupOutput() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "prefix %msg%n");
        val environment = new MockEnvironment()
                .withProperty("inquisitor.harness.logging.format", "markdown");
        val listener = new MarkdownLoggingApplicationListener(() -> loggerContext);

        assertThat(listener.getOrder())
                .isEqualTo(LoggingApplicationListener.DEFAULT_ORDER + 1);
        listener.onApplicationEvent(environmentPreparedEvent(environment));

        assertThat(console.layout().doLayout(event("Spring startup"))).isEmpty();
        assertThat(console.layout().doLayout(marked("## Harness event")))
                .matches("\\R\\d{2}:\\d{2}:\\d{2}\\.\\d{3}INFO\\R"
                        + "\\R    Harness event\\R");
    }

    @Test
    void earlyExclusiveInstallationUsesTheConfiguredPalette() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "prefix %msg%n");
        val environment = new MockEnvironment()
                .withProperty("inquisitor.harness.logging.format", "markdown")
                .withProperty("inquisitor.logging.markdown.palette", "nord");

        new MarkdownLoggingApplicationListener(() -> loggerContext)
                .onApplicationEvent(environmentPreparedEvent(environment));

        assertThat(console.layout().doLayout(marked("## Harness event")))
                .contains(
                        "48;2;59;66;82",
                        "48;2;163;190;140",
                        "38;2;143;188;187");
    }

    @Test
    void earlyExclusiveInstallationResolvesAServiceProvidedPalette() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "prefix %msg%n");
        val environment = new MockEnvironment()
                .withProperty("inquisitor.harness.logging.format", "markdown")
                .withProperty("inquisitor.logging.markdown.palette", "starter-test");

        new MarkdownLoggingApplicationListener(() -> loggerContext)
                .onApplicationEvent(environmentPreparedEvent(environment));

        assertThat(console.layout().doLayout(marked("## Harness event")))
                .contains("38;2;18;171;239");
    }

    @Test
    void earlyListenerLeavesSharedAndDisabledConsolesUntouched() {
        val sharedContext = loggerContext();
        val shared = console(sharedContext, "SHARED", "prefix %msg");
        val sharedEnvironment = new MockEnvironment()
                .withProperty("inquisitor.logging.markdown.console-mode", "shared");

        new MarkdownLoggingApplicationListener(() -> sharedContext)
                .onApplicationEvent(environmentPreparedEvent(sharedEnvironment));

        assertThat(shared.layout().doLayout(event("ordinary"))).isEqualTo("prefix ordinary");

        val disabledContext = loggerContext();
        val disabled = console(disabledContext, "DISABLED", "prefix %msg");
        val disabledEnvironment = new MockEnvironment()
                .withProperty("inquisitor.harness.logging.format", "markdown")
                .withProperty("inquisitor.logging.markdown.enabled", "false");

        new MarkdownLoggingApplicationListener(() -> disabledContext)
                .onApplicationEvent(environmentPreparedEvent(disabledEnvironment));

        assertThat(disabled.layout().doLayout(event("ordinary"))).isEqualTo("prefix ordinary");
    }

    @Test
    void registersTheEarlyListenerThroughSpringFactories() {
        assertThat(SpringFactoriesLoader.loadFactories(
                ApplicationListener.class, getClass().getClassLoader()))
                .anyMatch(MarkdownLoggingApplicationListener.class::isInstance);
    }

    @Test
    void explicitSharedModeOverridesHarnessMarkdownFormat() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "prefix %msg");

        runner(loggerContext)
                .withPropertyValues(
                        "inquisitor.harness.logging.format=markdown",
                        "inquisitor.logging.markdown.console-mode=shared")
                .run(context -> {
                    assertThat(context.getBean(MarkdownLoggingProperties.class).consoleMode())
                            .isEqualTo(MarkdownConsoleMode.SHARED);
                    assertThat(console.layout().doLayout(marked("## rendered")))
                            .isEqualTo("prefix     rendered");
                    assertThat(console.layout().doLayout(event("ordinary")))
                            .isEqualTo("prefix ordinary");
                    assertThat(loggerContext.getTurboFilterList()).isEmpty();
                });
    }

    @Test
    void explicitExclusiveModeWorksWithoutTheHarness() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER);
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "%msg");

        runner(loggerContext)
                .withPropertyValues("inquisitor.logging.markdown.console-mode=exclusive")
                .run(context -> {
                    assertThat(console.layout().doLayout(marked("**rendered**")))
                            .contains("INFO", "rendered")
                            .doesNotContain("**");
                    assertThat(console.layout().doLayout(event("ordinary"))).isEmpty();
                });
    }

    @Test
    void exclusiveInstallationIsIdempotentAndLeavesFileLayoutsRaw() {
        val loggerContext = loggerContext();
        val console = console(loggerContext, "CONSOLE", "prefix %msg%n");
        val fileLayout = file(loggerContext, "FILE", "%level %msg%n");
        val installer = new LogbackMarkdownInstaller(
                new FlexmarkAnsiRenderer(false), MarkdownConsoleMode.EXCLUSIVE, false);

        assertThat(installer.install(loggerContext)).isEqualTo(1);
        assertThat(installer.install(loggerContext)).isZero();
        assertThat(loggerContext.getTurboFilterList())
                .singleElement()
                .isInstanceOf(MarkdownMarkerTurboFilter.class);
        assertThat(console.layout().doLayout(event("ordinary"))).isEmpty();
        assertThat(console.layout().doLayout(marked("## rendered")))
                .contains("INFO", "rendered")
                .doesNotContain("prefix");
        assertThat(fileLayout.doLayout(event("ordinary")))
                .isEqualTo("INFO ordinary" + System.lineSeparator());
        assertThat(fileLayout.doLayout(marked("## raw")))
                .isEqualTo("INFO ## raw" + System.lineSeparator());
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
        assertThat(console.layout().doLayout(marked("## rendered"))).isEqualTo("    rendered");
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
        assertThat(console.layout().doLayout(marked("## once"))).isEqualTo("    once");
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
        assertThat(console.layout().doLayout(marked("## rendered"))).isEqualTo("    rendered");
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
        assertThat(layout.doLayout(marked("## rendered"))).isEqualTo("    rendered");
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
        assertThat(shortWord.layout().doLayout(marked("## short"))).isEqualTo("    short");
        assertThat(normalWord.layout().doLayout(marked("## normal"))).isEqualTo("    normal");
        assertThat(longWord.layout().doLayout(marked("## long"))).isEqualTo("    long");
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

    private static ApplicationEnvironmentPreparedEvent environmentPreparedEvent(
            MockEnvironment environment) {
        return new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(Object.class),
                new String[0],
                environment);
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
