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

package io.inquisitor.harness.logging.markdown;

import io.inquisitor.harness.logging.SqlLogger;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/** Marker-tagged Markdown SQL tool diagnostics. */
@Slf4j
public class MarkdownSqlLogger implements SqlLogger {

    @Override
    public void statementStarted(Request request) {
        val event = MarkdownLogSupport.event(breadcrumb(request, "EXECUTE"))
                .section("Statement", MarkdownLogSupport.codeBlock("sql", request.statement()));
        log.info(MarkdownLogSupport.marker(), event.message());
    }

    @Override
    public void statementCompleted(Request request, Response response) {
        val event = MarkdownLogSupport.event(breadcrumb(request, "SUCCESS"))
                .section("Result", MarkdownLogSupport.codeBlock("text", response.result()));
        log.info(MarkdownLogSupport.marker(), event.message());
    }

    @Override
    public void statementFailed(Request request, String error) {
        val breadcrumb = breadcrumb(request, "ERROR");
        val event = MarkdownLogSupport.event(breadcrumb)
                .content(MarkdownStepLogSupport.blockquote(error, breadcrumb.length()));
        log.info(MarkdownLogSupport.marker(), event.message());
    }

    private static String breadcrumb(Request request, String status) {
        return MarkdownBreadcrumbs.sql(request.datasource(), status);
    }
}
