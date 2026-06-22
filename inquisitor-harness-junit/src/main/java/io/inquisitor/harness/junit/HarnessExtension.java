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

package io.inquisitor.harness.junit;

import io.inquisitor.harness.HarnessDefaults;
import io.inquisitor.harness.tool.HttpTarget;
import io.inquisitor.harness.tool.HttpTargetRegistry;
import lombok.val;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Registers the application's HTTP target (from {@code local.server.port}) before
 * each test so {@link Scenario @Scenario} steps can reach the running app. Wired in
 * by {@link Harness @Harness}.
 *
 * <p>The target depends on the random server port, so it cannot be registered by
 * the starter autoconfiguration. When there is no web server (e.g. a SQL-only
 * suite), registration is skipped.
 */
public class HarnessExtension implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        val applicationContext = SpringExtension.getApplicationContext(context);
        val port = applicationContext.getEnvironment().getProperty("local.server.port");
        if (port != null) {
            applicationContext.getBean(HttpTargetRegistry.class)
                    .register(HarnessDefaults.APPLICATION, HttpTarget.of("http://localhost:" + port));
        }
    }
}
