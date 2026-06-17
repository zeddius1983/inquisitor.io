# Decisions

The "why" behind choices that aren't obvious from the code. For current status
see [roadmap.md](roadmap.md); for stable repo context see
[../CLAUDE.md](../CLAUDE.md). Append new entries; don't rewrite history.

## Build & language

- **Gradle convention plugins in `buildSrc/`** (not `includeBuild("build-logic")`).
  Simpler for a single-repo build; one place defines the Java/Spring/publish
  conventions that every module applies.
- **Java toolchain 26, but libraries compile to `release = 21`.** Keeps the
  published library modules consumable on Java 21 while developing on 26. Only
  `inquisitor-demo` opts into `release = 26` + `--enable-preview`, because it's
  an app (not published) and exercises the newest language features.
- **Version catalog (`libs.versions.toml`)** is the single source of dependency
  versions; module builds reference `libs.*`, never hardcode versions.
- **Root project is a published BOM** (`inquisitor-bom`) so consumers get
  aligned versions of the Inquisitor modules.

## Framework

- **Spring Data JDBC over JPA.** Explicit aggregates, AOT-processed queries,
  faster startup, no lazy-loading surprises. JPA only if entity count grows
  large enough to justify it.
- **Zero-config local run.** `inquisitor-demo-db-starter` autoconfigures a
  Postgres Testcontainer under the `local`/`unitTest` profiles and supplies a
  `JdbcConnectionDetails` bean *before* `DataSourceAutoConfiguration`, so a
  fresh clone runs with `./gradlew :inquisitor-demo:bootRun` and no manual
  database setup. Container reuse is enabled to keep restarts fast.
- **Spring AI as the harness engine.** Scenarios are natural-language markdown;
  an LLM interprets and drives the app, which is the whole premise of the tool.

## Testing

- **Scenarios as markdown test cases.** Human-readable `.md` files under
  `src/test/resources/scenarios/` are the integration tests; a single
  `@InquisitorTest`-annotated class discovers and runs them. Lowers the barrier
  to writing/maintaining integration tests.

> Conventions for code style live in the `java-developer` skill, not here. This
> file records project-specific decisions only.
