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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;

import io.inquisitor.harness.logging.SqlLogger;
import io.inquisitor.harness.logging.plain.PlainSqlLogger;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Tool that lets the LLM run a whole SQL script file — typically a fixture a scenario
 * names ("run accounts.sql") — against a registered datasource.
 *
 * <p>Scripts are only looked up under the configured script locations (by default
 * {@code classpath:fixtures/}), in any subdirectory: the model gives a file name or a
 * relative path, never a location, so it cannot reach other scripts on the classpath
 * (libraries ship schema-drop scripts, for instance). A name that matches several files
 * is rejected with the candidates listed, and a name that matches none lists the scripts
 * that are available, so the model can correct itself.
 *
 * <p>The script runs with Spring's {@link ResourceDatabasePopulator} — statements split
 * on {@code ;}, comments skipped — each statement committed as it runs; on an error the
 * statements before it stay applied. Errors are returned as a readable string rather
 * than thrown.
 */
public class SqlScriptTool {

    /** The default script location when none is configured. */
    public static final String DEFAULT_LOCATION = "classpath:fixtures/";

    private static final String DEFAULT_DATASOURCE = "default";
    private static final int MAX_LISTED = 50;

    private final DataSourceRegistry registry;
    private final List<String> locations;
    private final SqlLogger logger;
    private final ResourcePatternResolver resolver;

    public SqlScriptTool(DataSourceRegistry registry) {
        this(registry, List.of(DEFAULT_LOCATION), new PlainSqlLogger());
    }

    /**
     * @param registry  the datasources a script may run against
     * @param locations the {@code classpath:} roots scripts are looked up under
     * @param logger    narrates each script run
     */
    public SqlScriptTool(DataSourceRegistry registry, List<String> locations, SqlLogger logger) {
        this(registry, locations, logger, new PathMatchingResourcePatternResolver());
    }

    /** Looks scripts up on the given class loader's classpath (for tests). */
    SqlScriptTool(DataSourceRegistry registry, List<String> locations, SqlLogger logger, ClassLoader classLoader) {
        this(registry, locations, logger, new PathMatchingResourcePatternResolver(classLoader));
    }

    private SqlScriptTool(
            DataSourceRegistry registry, List<String> locations, SqlLogger logger, ResourcePatternResolver resolver) {
        this.registry = registry;
        this.locations = locations.stream()
                .map(location -> location.endsWith("/") ? location : location + "/")
                .toList();
        this.logger = logger;
        this.resolver = resolver;
    }

    @Tool(description = "Run a SQL script file (e.g. a test fixture such as accounts.sql) against a datasource. "
            + "Give the script's file name, or a relative path like transfers/seed.sql, exactly as the scenario "
            + "names it; it is looked up among the test's SQL scripts. Every statement in the file is executed. "
            + "Use this whenever a step says to run or apply a script file, instead of retyping its contents.")
    public String sqlScript(
            @ToolParam(required = false,
                    description = "datasource name; omit when there is only one (the application's database)")
            @Nullable String datasource,
            @ToolParam(description = "the script's file name or relative path, e.g. accounts.sql") String script) {

        val name = script.strip();
        val invalid = validate(name);
        if (invalid != null) {
            return invalid;
        }
        val matches = find(name);
        if (matches.isEmpty()) {
            return "SQL script not found: '" + name + "' under " + locations + ". Available scripts: " + available();
        }
        if (matches.size() > 1) {
            return "Ambiguous SQL script name '" + name + "': it matches " + describe(matches)
                    + ". Give a more specific relative path.";
        }
        val location = matches.getFirst().location();
        val resource = matches.getFirst().resource();

        val request = new SqlLogger.Request(displayDatasourceName(datasource), "-- " + location + "\n" + read(resource));
        logger.statementStarted(request);
        try {
            val populator = new ResourceDatabasePopulator(resource);
            populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            populator.execute(registry.resolve(datasource));
            val result = "Executed SQL script " + location + ".";
            logger.statementCompleted(request, new SqlLogger.Response(result));
            return result;
        } catch (DataAccessException e) {
            val error = "SQL script error in " + location + ": " + e.getMostSpecificCause().getMessage()
                    + " (statements before the failing one were applied)";
            logger.statementFailed(request, error);
            return error;
        }
    }

    /** Rejects anything but a plain relative file path, so a name can't escape the locations. */
    private static @Nullable String validate(String name) {
        if (name.isEmpty()) {
            return "Invalid SQL script name: it is empty.";
        }
        if (name.startsWith("/") || name.startsWith("\\") || name.contains(":") || name.contains("..")
                || name.contains("*") || name.contains("?") || name.contains("\\")) {
            return "Invalid SQL script name '" + name + "': give a file name or relative path such as "
                    + "accounts.sql or transfers/seed.sql (no '..', wildcards, absolute paths, or locations).";
        }
        if (!name.endsWith(".sql")) {
            return "Invalid SQL script name '" + name + "': it must be a .sql file.";
        }
        return null;
    }

    /** A script found under a location: where it is shown as, and the file itself. */
    private record Match(String location, Resource resource, String url) {
    }

    /**
     * Every distinct script file matching {@code name} under the locations. Files are told
     * apart by their physical URL, not their display location: two classpath entries (say,
     * the test resources and a test-support jar) may each hold {@code fixtures/seed.sql},
     * and that must be reported as ambiguous rather than silently running one of them.
     */
    private List<Match> find(String name) {
        val matches = new LinkedHashMap<String, Match>();
        for (val location : locations) {
            for (val resource : resources(location, "**/" + name)) {
                // Match whole path segments: "seed.sql" must not match "preseed.sql".
                val relative = relative(location, resource);
                val url = url(resource);
                if (relative != null && url != null && (relative.equals(name) || relative.endsWith("/" + name))) {
                    matches.putIfAbsent(url, new Match(location + relative, resource, url));
                }
            }
        }
        return List.copyOf(matches.values());
    }

    /** The candidates' display locations, with their origin where two would read the same. */
    private static List<String> describe(List<Match> matches) {
        return matches.stream()
                .map(match -> matches.stream().filter(other -> other.location().equals(match.location())).count() > 1
                        ? match.location() + " (from " + match.url() + ")"
                        : match.location())
                .toList();
    }

    private static @Nullable String url(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return null;
        }
    }

    /** The scripts available under the locations, for a not-found message. */
    private List<String> available() {
        val names = new TreeSet<String>();
        for (val location : locations) {
            for (val resource : resources(location, "**/*.sql")) {
                val relative = relative(location, resource);
                if (relative != null) {
                    names.add(relative);
                }
            }
        }
        val listed = new ArrayList<>(names);
        return listed.size() > MAX_LISTED ? listed.subList(0, MAX_LISTED) : listed;
    }

    /** {@code classpath:} roots are searched across every classpath entry ({@code classpath*:}). */
    private Resource[] resources(String location, String pattern) {
        try {
            return resolver.getResources(searchable(location) + pattern);
        } catch (IOException e) {
            return new Resource[0];
        }
    }

    /** The resource's path below whichever copy of {@code location} contains it. */
    private @Nullable String relative(String location, Resource resource) {
        try {
            val url = resource.getURL().toString();
            for (val root : resolver.getResources(searchable(location))) {
                val rootUrl = root.getURL().toString();
                if (url.startsWith(rootUrl)) {
                    return url.substring(rootUrl.length());
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String searchable(String location) {
        return location.startsWith(ResourceLoader.CLASSPATH_URL_PREFIX)
                ? ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                        + location.substring(ResourceLoader.CLASSPATH_URL_PREFIX.length())
                : location;
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read SQL script " + resource, e);
        }
    }

    private static String displayDatasourceName(@Nullable String datasource) {
        return datasource == null || datasource.isBlank()
                ? DEFAULT_DATASOURCE
                : datasource.strip();
    }
}
