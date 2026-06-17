# Roadmap

Volatile status — what's done and what's next. For stable repo context see
[../CLAUDE.md](../CLAUDE.md); for the rationale behind choices see
[decisions.md](decisions.md).

The authoritative status is git history + this table.

## Status

| # | Task | Status |
|---|------|--------|
| 01 | Gradle 9 multi-module build scaffold | ✅ done |
| 02 | `inquisitor-demo` banking REST service | ✅ done |
| —  | `inquisitor-demo-db-starter` (zero-config local Postgres) | ✅ done |
| 03 | `inquisitor-harness` — core scenario execution | 🚧 in progress (spike: `ChatClientExample.java`, untracked) |
| 04 | `inquisitor-harness-starter` — autoconfiguration | ⏳ pending |
| 05 | `inquisitor-harness-junit` + `-junit-starter` | ⏳ pending (annotation stub exists) |
| 06 | `inquisitor-mock` + `inquisitor-mock-starter` | ⏳ reserved, not started |

## Now

- Build out `inquisitor-harness`: markdown scenario parser (flexmark), Spring AI
  `ChatClient` orchestration, and the executor that drives the app under test.

## Next

- Wire the harness into the JUnit extension so `@InquisitorTest` actually runs
  the scenarios in `inquisitor-demo/src/test/resources/scenarios/`.
- Spring Boot autoconfiguration starters for harness and JUnit extension.
- Decide on the mock-server design before implementing the `*-mock` modules.

> Keep this file current as tasks complete; move durable rationale into
> [decisions.md](decisions.md), not here.
