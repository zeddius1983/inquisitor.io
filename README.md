# Inquisitor

[![build](https://github.com/zeddius1983/inquisitor.io/actions/workflows/build.yml/badge.svg)](https://github.com/zeddius1983/inquisitor.io/actions/workflows/build.yml)

**LLM-driven integration testing for Spring applications.**

Write your integration tests as human-readable markdown scenarios. An
LLM-backed harness (built on [Spring AI](https://spring.io/projects/spring-ai))
reads each scenario, drives your running application through real HTTP and SQL,
and verifies the outcome — step by step.

Inquisitor ships as a Spring Boot 4 starter: add one test dependency, annotate a
test class, and drop your scenarios under `src/test/resources/scenarios/`.

```markdown
# Transfer between accounts

## Step: open two accounts
**Intent:** create a source and a destination account
...

## Step: move money
**Intent:** transfer 50 from the first account to the second
...

**Expected response**
- the source balance is reduced by 50
- the destination balance is increased by 50
```

```java
@Harness
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ScenarioSuiteTest {
    @Scenario void transferBetweenAccounts() {}
}
```

Each `## Step` is reported as its own sub-test (like a parameterized test's
invocations); the first failing step fails and the rest are skipped.

## How it works

- You describe **intent** and **expected outcome** in markdown — not request
  bodies and assertions in code.
- The harness hands the scenario to an LLM with two tools: `httpRequest` (drives
  your app over HTTP) and `sqlQuery` (inspects the database). The model decides
  what to call and judges each step's result.
- All steps in a scenario share one conversation, so an id created in step 1
  flows naturally into step 3 — no templating or regex.

> [!WARNING]
> **An LLM drives and judges these tests, and LLMs are non-deterministic.** The
> same scenario can pass on one run and fail on another, a weak model may
> hallucinate a tool result or rubber-stamp a step it never actually verified,
> and a verdict is only ever as trustworthy as the model behind it. Treat
> Inquisitor as a complement to deterministic tests, not a replacement — keep
> hard correctness guarantees in conventional unit/integration tests.
>
> To keep results as stable as possible:
> - **Choose the model wisely.** Prefer a capable, instruction-following model
>   and run it at **temperature 0**. Inquisitor's own suite uses a dense local
>   `gemma-4-31B`; MoE models tend to shortcut multi-step scenarios by answering
>   from chat memory instead of calling the tools (see
>   [docs/decisions.md](docs/decisions.md)).
> - **Write unambiguous scenarios.** Spell out the intent and the expected
>   outcome; the less the model has to guess, the more repeatable the verdict.
> - **Don't gate critical CI on it blindly.** Review failures — a red step may be
>   a real regression, model flakiness, or an ambiguous scenario.

## Modules

| Module | Role |
|--------|------|
| `inquisitor-harness` | Core scenario execution; Spring AI `ChatClient` orchestration. Parses markdown scenarios (flexmark) and drives the app. |
| `inquisitor-harness-starter` | Spring Boot autoconfiguration for the harness. |
| `inquisitor-harness-junit` | JUnit 5 layer: `@Harness` on the class + one `@Scenario` method per scenario, each step a sub-test. |
| `inquisitor-harness-junit-starter` | Autoconfiguration for the JUnit layer — the single dependency a consumer needs. |
| `inquisitor-bom` | Platform BOM aligning the Inquisitor module versions. |
| `inquisitor-demo` | Banking REST demo app + scenario tests; the reference consumer. |

`inquisitor-mock` / `inquisitor-mock-starter` are reserved for a future mock
server and not yet implemented.

## Getting started

Add the JUnit starter and the BOM to your build (test scope):

```kotlin
dependencies {
    testImplementation(platform("io.inquisitor:inquisitor-bom:<version>"))
    testImplementation("io.inquisitor:inquisitor-harness-junit-starter")
}
```

Point the harness at an OpenAI-compatible chat model via the standard
`spring.ai.openai.*` properties (Inquisitor uses a local
[gemma-4-31B](docs/decisions.md) at temperature 0 for its own tests — dense
models follow multi-step tool calls more reliably than MoE ones):

```yaml
spring:
  ai:
    openai:
      base-url: http://localhost:8000
      api-key: not-needed-for-local
      chat:
        model: gemma-4-31B-it-QAT-Q4_0
        temperature: 0.0
```

Then write a `@Harness` test class with one `@Scenario` method per markdown file
under `src/test/resources/scenarios/`. The scenario file is resolved from the
method name (`transferBetweenAccounts()` → `transfer-between-accounts.md`) or set
explicitly with `@Scenario("classpath:scenarios/custom.md")`.

## Building

Requires a Java 26 toolchain (Gradle provisions it) and, to run the demo,
Docker/Podman for Testcontainers.

```bash
./gradlew build                       # compile + test everything
./gradlew :inquisitor-demo:bootRun    # run the demo app (local profile)
```

The scenario suites that actually call an LLM are gated behind the
`INQUISITOR_LLM_IT=true` environment variable, so a plain `./gradlew build`
stays green without a running model:

```bash
INQUISITOR_LLM_IT=true ./gradlew :inquisitor-demo:test
```

The demo's `local` profile starts a Postgres Testcontainer automatically
(`postgres:17-alpine`, reuse enabled) and runs Flyway migrations — no manual
database setup.

## Releasing to Maven Central

The four library modules plus `inquisitor-bom` publish to Maven Central through
the [Central Portal](https://central.sonatype.com) via the
[Vanniktech maven-publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/)
(`inquisitor.publish-conventions`). The demo and the (unimplemented) mock modules
are not published.

**One-time setup** (none of this lives in the repo):

1. Create a Central Portal account and **verify the `io.inquisitor` namespace** —
   the Portal gives you a DNS `TXT` record to add to the `inquisitor.io` domain.
2. Generate a GPG key and publish its public half to a keyserver:
   ```bash
   gpg --gen-key
   gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
   ```
3. Generate a **user token** under your Central Portal account settings.
4. Put the credentials in `~/.gradle/gradle.properties`:
   ```properties
   mavenCentralUsername=<portal-token-username>
   mavenCentralPassword=<portal-token-password>

   signingInMemoryKey=<armored-private-key>
   signingInMemoryKeyPassword=<key-passphrase>
   ```
   `signingInMemoryKey` must be the **whole** ASCII-armored private key on one
   line with the line breaks escaped as `\n` — including the blank line after the
   `-----BEGIN-----` header. Generate it with:
   ```bash
   gpg --armor --export-secret-keys <KEY_ID> | awk '{sub(/\r/,""); printf "%s\\n", $0;}'
   ```

**Cut a release locally:**

1. Set the release `version` in `gradle.properties` (currently `0.1.0`).
2. Upload, then release from the Portal (publishing tasks aren't
   configuration-cache compatible, so disable it):
   ```bash
   ./gradlew publishToMavenCentral --no-configuration-cache          # staged deployment
   # or, to upload and auto-release in one step:
   ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
   ```
   Tip: `./gradlew publishToMavenLocal --no-configuration-cache` signs and writes
   the artifacts to `~/.m2` so you can verify your key/token wiring without
   touching Central.

**Or release from CI.** The [`release`](.github/workflows/release.yml) workflow
runs `build` then `publishAndReleaseToMavenCentral` when a `v*` tag is pushed (or
on manual dispatch). It reads the same credentials from repository secrets —
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` (the armored
private key), `SIGNING_KEY_PASSWORD` — so set those under **Settings → Secrets and
variables → Actions**, then:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Every push and PR is verified by the [`build`](.github/workflows/build.yml)
workflow (`./gradlew build` on a JDK 26 runner with Docker for Testcontainers).

> Note: the harness depends on Spring AI `2.0.0-RC1`, which lives in
> `repo.spring.io/milestone` (not Central). Until a GA Spring AI lands, consumers
> need that milestone repository on their build to resolve transitive deps.

## Documentation

- [CLAUDE.md](CLAUDE.md) — stable repo context and conventions.
- [docs/roadmap.md](docs/roadmap.md) — what's done and what's next.
- [docs/decisions.md](docs/decisions.md) — the "why" behind the design choices.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
