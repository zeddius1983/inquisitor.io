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

package io.inquisitor.harness.tool;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;

/**
 * Tool that lets the LLM run SQL (read <em>and</em> write) against a registered
 * datasource — typically to set up or verify state around the API calls it makes.
 *
 * <p>A statement producing a result set (SELECT, etc.) is returned as rows; any
 * other statement returns the affected row count. SQL errors are returned as a
 * readable string rather than thrown.
 */
public class SqlTool {

    private final DataSourceRegistry registry;

    public SqlTool(DataSourceRegistry registry) {
        this.registry = registry;
    }

    @Tool(description = "Execute a SQL statement against a datasource. A query (SELECT) returns its rows; "
            + "an INSERT/UPDATE/DELETE/DDL statement returns the number of affected rows. "
            + "Use this to set up or verify database state.")
    public String sqlQuery(
            @ToolParam(required = false,
                    description = "datasource name; omit when there is only one (the application's database)")
            @Nullable String datasource,
            @ToolParam(description = "the SQL statement to execute") String sql) {

        val jdbcTemplate = new JdbcTemplate(registry.resolve(datasource));
        try {
            return jdbcTemplate.execute((StatementCallback<String>) statement -> {
                if (statement.execute(sql)) {
                    try (val resultSet = statement.getResultSet()) {
                        return formatRows(resultSet);
                    }
                }
                return "Updated " + statement.getUpdateCount() + " row(s).";
            });
        } catch (DataAccessException e) {
            return "SQL error: " + e.getMostSpecificCause().getMessage();
        }
    }

    private static String formatRows(@Nullable ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            return "0 row(s): []";
        }
        val metaData = resultSet.getMetaData();
        val columnCount = metaData.getColumnCount();
        val rows = new ArrayList<Map<String, Object>>();
        while (resultSet.next()) {
            val row = new LinkedHashMap<String, Object>();
            for (int column = 1; column <= columnCount; column++) {
                row.put(metaData.getColumnLabel(column), resultSet.getObject(column));
            }
            rows.add(row);
        }
        return rows.size() + " row(s): " + rows;
    }
}
