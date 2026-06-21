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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies {@link SqlTool} against a real Postgres database. Skipped (not failed)
 * when Docker is unavailable.
 */
class SqlToolTest {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17-alpine");

    private static SqlTool tool;

    @BeforeAll
    static void startDatabase() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        CONTAINER.start();

        val dataSource = new DriverManagerDataSource(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        new JdbcTemplate(dataSource).execute(
                "CREATE TABLE item (id SERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL)");

        val registry = new DataSourceRegistry();
        registry.register("app", dataSource);
        tool = new SqlTool(registry);
    }

    @AfterAll
    static void stopDatabase() {
        if (CONTAINER.isRunning()) {
            CONTAINER.stop();
        }
    }

    @Test
    void writeReturnsAffectedRowCount() {
        val result = tool.sqlQuery(null, "INSERT INTO item (name) VALUES ('alice')");

        assertThat(result).isEqualTo("Updated 1 row(s).");
    }

    @Test
    void queryReturnsRows() {
        tool.sqlQuery(null, "INSERT INTO item (name) VALUES ('bob')");

        val result = tool.sqlQuery(null, "SELECT name FROM item WHERE name = 'bob'");

        assertThat(result).contains("1 row(s)").contains("name=bob");
    }

    @Test
    void sqlErrorIsReturnedNotThrown() {
        val result = tool.sqlQuery(null, "SELECT * FROM does_not_exist");

        assertThat(result).startsWith("SQL error:");
    }

    @Test
    void rejectsUnknownDatasource() {
        assertThatThrownBy(() -> tool.sqlQuery("other", "SELECT 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown datasource 'other'");
    }
}
