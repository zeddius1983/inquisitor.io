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

package io.inquisitor.harness.junit.fixtures;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * A database-free {@link DataSource} that records every SQL statement executed through it,
 * in order — enough for Spring's {@code @Sql} machinery, which only creates statements and
 * executes them. The harness tests assert on <em>which</em> fixture statements ran and
 * <em>when</em>, not on what a database would make of them.
 */
public class RecordingDataSource implements DataSource {

    private final List<String> statements = new CopyOnWriteArrayList<>();

    /** Every statement executed so far, in order. */
    public List<String> statements() {
        return List.copyOf(statements);
    }

    /** How many executed statements contain {@code text}. */
    public long count(String text) {
        return statements.stream().filter(statement -> statement.contains(text)).count();
    }

    @Override
    public Connection getConnection() {
        return proxy(Connection.class, (method, args) -> switch (method) {
            case "createStatement", "prepareStatement" -> statement();
            case "isClosed", "isReadOnly" -> false;
            case "getAutoCommit" -> true;
            default -> null;
        });
    }

    @Override
    public Connection getConnection(String username, String password) {
        return getConnection();
    }

    private Statement statement() {
        return proxy(Statement.class, (method, args) -> {
            if (method.startsWith("execute") && args != null && args.length > 0 && args[0] instanceof String sql) {
                statements.add(sql.strip());
            }
            return switch (method) {
                // No result set, and -1 = "no more results": callers that drain the results
                // (ScriptUtils does) stop here instead of looping forever.
                case "execute", "getMoreResults", "isClosed" -> false;
                case "getUpdateCount" -> -1;
                case "executeUpdate" -> 0;
                default -> null;
            };
        });
    }

    /** A JDK proxy answering by method name; primitive returns default to zero/false. */
    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            var result = answer.apply(method.getName(), args);
            return result != null ? result : primitiveDefault(method.getReturnType());
        }));
    }

    /** {@code false} / zero for a primitive return type, so an unanswered call can't NPE. */
    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @FunctionalInterface
    private interface Answer {
        Object apply(String method, Object[] args) throws Exception;
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return type.cast(this);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }
}
