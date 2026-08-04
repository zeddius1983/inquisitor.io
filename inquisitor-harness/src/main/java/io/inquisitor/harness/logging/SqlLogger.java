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

package io.inquisitor.harness.logging;

/** Presentation seam for SQL tool statement and result diagnostics. */
public interface SqlLogger {

    /** Invoked immediately before a statement is executed. */
    void statementStarted(Request request);

    /** Invoked after a statement executes successfully. */
    void statementCompleted(Request request, Response response);

    /** Invoked when statement execution fails. */
    void statementFailed(Request request, String error);

    /** The resolved datasource and SQL statement made available to loggers. */
    record Request(String datasource, String statement) {
    }

    /** The formatted result returned by the SQL tool. */
    record Response(String result) {
    }
}
