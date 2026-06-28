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

package io.inquisitor.demo;

import java.util.Optional;

import io.inquisitor.demo.service.AccountServiceRouter;
import io.inquisitor.demo.service.Bug;
import lombok.val;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Flips a seeded {@link Bug} on the {@link AccountServiceRouter} around an
 * {@link EnableBug @EnableBug} scenario method. Registered via {@code @EnableBug}'s
 * meta-annotation, so it only runs for methods that declare a bug.
 *
 * <p>A {@code @Scenario} method is a {@code @TestTemplate}, so JUnit brackets each
 * step invocation with before/after-each: the bug is (re-)enabled before every step
 * and cleared after, leaving it active for the whole scenario and reset once done.
 */
public class BugInjectionExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        bug(context).ifPresent(b -> router(context).enableBug(b));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        router(context).disableAllBugs();
    }

    private static Optional<Bug> bug(ExtensionContext context) {
        return context.getTestMethod()
                .flatMap(method -> AnnotationSupport.findAnnotation(method, EnableBug.class))
                .map(EnableBug::value);
    }

    private static AccountServiceRouter router(ExtensionContext context) {
        val applicationContext = SpringExtension.getApplicationContext(context);
        return applicationContext.getBean(AccountServiceRouter.class);
    }
}
