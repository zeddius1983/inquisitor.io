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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.pattern.DynamicConverter;
import ch.qos.logback.core.spi.AppenderAttachable;
import io.inquisitor.logback.markdown.MarkdownMessageConverter;
import io.inquisitor.logback.markdown.MarkdownRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;

@Slf4j
@RequiredArgsConstructor
class LogbackMarkdownInstaller {

    private static final List<String> MESSAGE_WORDS = List.of("m", "msg", "message");

    private final MarkdownRenderer renderer;

    int install(ILoggerFactory loggingSystem) {
        if (!(loggingSystem instanceof LoggerContext context)) {
            log.debug("Skipping automatic Markdown logging: active SLF4J backend is not Logback");
            return 0;
        }
        return install(context);
    }

    int install(LoggerContext context) {
        val configurationLock = context.getConfigurationLock();
        configurationLock.lock();
        try {
            val appenders = reachableAppenders(context);
            int installed = 0;
            for (val appender : appenders) {
                if (!(appender instanceof ConsoleAppender<?> consoleAppender)) {
                    continue;
                }
                val layout = patternLayout(consoleAppender);
                if (layout == null) {
                    log.debug(
                            "Skipping console appender '{}': encoder does not expose a PatternLayout",
                            appender.getName());
                    continue;
                }
                if (install(layout)) {
                    installed++;
                }
            }
            log.debug("Automatic Markdown logging installed into {} console layout(s)", installed);
            return installed;
        }
        finally {
            configurationLock.unlock();
        }
    }

    private boolean install(PatternLayout layout) {
        val converterMap = layout.getInstanceConverterMap();
        if (MESSAGE_WORDS.stream()
                .allMatch(word -> converterMap.get(word) instanceof MarkdownConverterSupplier)) {
            return false;
        }

        val previous = new HashMap<String, Supplier<DynamicConverter>>();
        val previouslyAbsent = new HashSet<String>();
        for (val word : MESSAGE_WORDS) {
            if (converterMap.containsKey(word)) {
                previous.put(word, converterMap.get(word));
            }
            else {
                previouslyAbsent.add(word);
            }
        }

        val supplier = new MarkdownConverterSupplier(renderer);
        val wasStarted = layout.isStarted();
        try {
            MESSAGE_WORDS.forEach(word -> converterMap.put(word, supplier));
            if (wasStarted) {
                layout.start();
                if (!layout.isStarted()) {
                    throw new IllegalStateException("PatternLayout did not restart");
                }
            }
            return true;
        }
        catch (RuntimeException exception) {
            restore(converterMap, previous, previouslyAbsent);
            if (wasStarted) {
                try {
                    layout.start();
                }
                catch (RuntimeException recoveryException) {
                    exception.addSuppressed(recoveryException);
                }
            }
            log.debug("Could not install automatic Markdown conversion into a console layout", exception);
            return false;
        }
    }

    private static Set<Appender<ILoggingEvent>> reachableAppenders(LoggerContext context) {
        val appenders = Collections.newSetFromMap(
                new IdentityHashMap<Appender<ILoggingEvent>, Boolean>());
        val pending = new ArrayDeque<Appender<ILoggingEvent>>();
        for (Logger logger : context.getLoggerList()) {
            logger.iteratorForAppenders().forEachRemaining(pending::addLast);
        }
        while (!pending.isEmpty()) {
            val appender = pending.removeFirst();
            if (!appenders.add(appender)) {
                continue;
            }
            enqueueAttachedAppenders(appender, pending);
        }
        return appenders;
    }

    @SuppressWarnings("unchecked")
    private static void enqueueAttachedAppenders(
            Appender<ILoggingEvent> appender,
            Deque<Appender<ILoggingEvent>> pending) {
        if (appender instanceof AppenderAttachable<?> attachable) {
            val loggingEventAttachable = (AppenderAttachable<ILoggingEvent>) attachable;
            loggingEventAttachable.iteratorForAppenders().forEachRemaining(pending::addLast);
        }
    }

    private static @Nullable PatternLayout patternLayout(ConsoleAppender<?> appender) {
        Encoder<?> encoder = appender.getEncoder();
        if (!(encoder instanceof LayoutWrappingEncoder<?> layoutEncoder)) {
            return null;
        }
        return layoutEncoder.getLayout() instanceof PatternLayout layout ? layout : null;
    }

    private static void restore(
            Map<String, Supplier<DynamicConverter>> converterMap,
            Map<String, Supplier<DynamicConverter>> previous,
            Set<String> previouslyAbsent) {
        previous.forEach(converterMap::put);
        previouslyAbsent.forEach(converterMap::remove);
    }

    private record MarkdownConverterSupplier(MarkdownRenderer renderer)
            implements Supplier<DynamicConverter> {

        @Override
        public DynamicConverter get() {
            return new MarkdownMessageConverter(renderer, true);
        }
    }
}
