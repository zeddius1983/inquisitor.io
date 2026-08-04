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

package io.inquisitor.harness.logging.plain;

import io.inquisitor.harness.logging.SqlLogger;
import lombok.extern.slf4j.Slf4j;

/** Conventional SQL tool diagnostics. */
@Slf4j
public class PlainSqlLogger implements SqlLogger {

    @Override
    public void statementStarted(Request request) {
        log.debug("sqlQuery <- datasource={}, sql={}",
                request.datasource(), request.statement());
    }

    @Override
    public void statementCompleted(Request request, Response response) {
        log.debug("sqlQuery -> {}", response.result());
    }

    @Override
    public void statementFailed(Request request, String error) {
        log.debug("sqlQuery -> {}", error);
    }
}
