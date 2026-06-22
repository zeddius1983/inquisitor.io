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
| 03 | `inquisitor-harness` — core scenario execution (parser, tools, executor) | ✅ done |
| 04 | `inquisitor-harness-starter` — autoconfiguration | ✅ done |
| 05 | Standalone harness wired into `inquisitor-demo` (`@Test`-per-scenario), green on the local model | ✅ done |
| 05b | `inquisitor-harness-junit` + `-junit-starter` (`@InquisitorTest` ergonomic layer) | ⏳ deferred (modules are reserved scaffolding) |
| 06 | `inquisitor-mock` + `inquisitor-mock-starter` | ⏳ reserved, not started |

## Now

- Phase 5 is green: the autoconfigured standalone harness drives the demo over
  real HTTP/Postgres with the local model; all five scenarios pass.

## Next

- **OpenAPI discovery** so scenarios can drop explicit request bodies and use
  natural-language intent only (add springdoc to the demo; let the model read the
  spec). Until then scenarios keep explicit bodies.
- Build the ergonomic `@InquisitorTest` JUnit layer (`@TestFactory`/`@TestTemplate`)
  in `inquisitor-harness-junit{,-starter}` once the core is trusted.
- Decide on the mock-server design before implementing the `*-mock` modules.

> Keep this file current as tasks complete; move durable rationale into
> [decisions.md](decisions.md), not here.
