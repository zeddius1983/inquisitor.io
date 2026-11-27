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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.inquisitor.harness.logging.SqlLogger;
import io.inquisitor.harness.logging.plain.PlainSqlLogger;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Verifies {@link SqlScriptTool} against a real Postgres database, with scripts under the
 * default {@code classpath:fixtures/} location of the test resources. Skipped (not
 * failed) when Docker is unavailable.
 */
class SqlScriptToolTest {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17-alpine");

    private static DataSourceRegistry registry;
    private static JdbcTemplate jdbc;
    private static SqlScriptTool tool;

    @BeforeAll
    static void startDatabase() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        CONTAINER.start();

        val dataSource = new DriverManagerDataSource(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE gadget (id SERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL)");

        registry = new DataSourceRegistry();
        registry.register("app", dataSource);
        tool = new SqlScriptTool(registry);
    }

    @AfterAll
    static void stopDatabase() {
        if (CONTAINER.isRunning()) {
            CONTAINER.stop();
        }
    }

    @BeforeEach
    void emptyTable() {
        jdbc.execute("DELETE FROM gadget");
    }

    @Test
    void runsEveryStatementOfAScriptGivenByName() {
        val result = tool.sqlScript(null, "seed-gadgets.sql");

        assertThat(result).isEqualTo("Executed SQL script classpath:fixtures/seed-gadgets.sql.");
        assertThat(names()).containsExactly("alpha", "beta");
    }

    @Test
    void findsAScriptInAnySubdirectoryByNameOrRelativePath() {
        assertThat(tool.sqlScript(null, "seed-more-gadgets.sql"))
                .isEqualTo("Executed SQL script classpath:fixtures/nested/seed-more-gadgets.sql.");
        assertThat(tool.sqlScript(null, "nested/seed-more-gadgets.sql"))
                .isEqualTo("Executed SQL script classpath:fixtures/nested/seed-more-gadgets.sql.");
        assertThat(names()).containsExactly("gamma", "gamma");
    }

    @Test
    void matchesWholeFileNamesOnly() {
        // "gadgets.sql" is a suffix of both seed scripts' names, but neither's file name.
        val result = tool.sqlScript(null, "gadgets.sql");

        assertThat(result).startsWith("SQL script not found: 'gadgets.sql'");
        assertThat(names()).isEmpty();
    }

    @Test
    void ambiguousNameListsTheCandidatesAndRunsNothing() {
        val result = tool.sqlScript(null, "dup.sql");

        assertThat(result)
                .startsWith("Ambiguous SQL script name 'dup.sql'")
                .contains("classpath:fixtures/a/dup.sql", "classpath:fixtures/b/dup.sql");
        assertThat(names()).isEmpty();
        assertThat(tool.sqlScript(null, "b/dup.sql")).startsWith("Executed");
        assertThat(names()).containsExactly("dup-b");
    }

    @Test
    void sameRelativePathInTwoClasspathEntriesIsAmbiguous(@TempDir Path testResources, @TempDir Path supportJar)
            throws IOException {
        // Two classpath entries that each hold fixtures/twin.sql — e.g. the test resources
        // and a test-support dependency. They read the same, but are different files.
        for (val root : List.of(testResources, supportJar)) {
            Files.createDirectories(root.resolve("fixtures"));
            Files.writeString(root.resolve("fixtures/twin.sql"),
                    "INSERT INTO gadget (name) VALUES ('" + root.getFileName() + "');");
        }
        val classLoader = new URLClassLoader(
                new URL[] {testResources.toUri().toURL(), supportJar.toUri().toURL()}, null);
        val twins = new SqlScriptTool(
                registry, List.of(SqlScriptTool.DEFAULT_LOCATION), new PlainSqlLogger(), classLoader);

        val result = twins.sqlScript(null, "twin.sql");

        assertThat(result)
                .startsWith("Ambiguous SQL script name 'twin.sql'")
                .contains("classpath:fixtures/twin.sql (from " + testResources.toUri().toURL())
                .contains("classpath:fixtures/twin.sql (from " + supportJar.toUri().toURL());
        assertThat(names()).isEmpty();
    }

    @Test
    void unknownNameListsTheAvailableScripts() {
        val result = tool.sqlScript(null, "missing.sql");

        assertThat(result)
                .startsWith("SQL script not found: 'missing.sql' under [classpath:fixtures/]")
                .contains("seed-gadgets.sql", "nested/seed-more-gadgets.sql", "a/dup.sql");
    }

    @Test
    void scriptsOutsideTheLocationsAreUnreachable() {
        assertThat(tool.sqlScript(null, "drop-gadgets.sql")).startsWith("SQL script not found");
        assertThat(tool.sqlScript(null, "../outside/drop-gadgets.sql")).startsWith("Invalid SQL script name");
        assertThat(tool.sqlScript(null, "classpath:outside/drop-gadgets.sql")).startsWith("Invalid SQL script name");
        assertThat(tool.sqlScript(null, "/outside/drop-gadgets.sql")).startsWith("Invalid SQL script name");
        assertThat(tool.sqlScript(null, "*.sql")).startsWith("Invalid SQL script name");
        assertThat(tool.sqlScript(null, "seed-gadgets")).isEqualTo(
                "Invalid SQL script name 'seed-gadgets': it must be a .sql file.");

        assertThat(jdbc.queryForObject("SELECT to_regclass('gadget') IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test
    void configuredLocationsReplaceTheDefault() {
        val outside = new SqlScriptTool(registry, List.of("classpath:outside"), new PlainSqlLogger());

        assertThat(outside.sqlScript(null, "seed-gadgets.sql")).startsWith("SQL script not found");
    }

    @Test
    void errorIsReturnedAndEarlierStatementsStayApplied() {
        val result = tool.sqlScript(null, "half-broken.sql");

        assertThat(result)
                .startsWith("SQL script error in classpath:fixtures/half-broken.sql:")
                .contains("no_such_table")
                .endsWith("(statements before the failing one were applied)");
        assertThat(names()).containsExactly("before-error");
    }

    @Test
    void logsTheScriptWithItsLocationAndContent() {
        val logger = mock(SqlLogger.class);
        val loggedTool = new SqlScriptTool(registry, List.of(SqlScriptTool.DEFAULT_LOCATION), logger);

        loggedTool.sqlScript(null, "nested/seed-more-gadgets.sql");

        val request = ArgumentCaptor.forClass(SqlLogger.Request.class);
        verify(logger).statementStarted(request.capture());
        verify(logger).statementCompleted(any(), any());
        assertThat(request.getValue().datasource()).isEqualTo("default");
        assertThat(request.getValue().statement()).isEqualTo(
                "-- classpath:fixtures/nested/seed-more-gadgets.sql\nINSERT INTO gadget (name) VALUES ('gamma');");
    }

    private static List<String> names() {
        return jdbc.queryForList("SELECT name FROM gadget ORDER BY id", String.class);
    }
}
