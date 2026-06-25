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
| 05b | `inquisitor-harness-junit` + `-junit-starter` (`@Harness`/`@Scenario` ergonomic layer) | ✅ done |
| —  | Scenario restructure: `positive`/`negative` × `explicit`/`cucumber`/`intent` style buckets | ✅ done |
| 09 | OpenAPI/Swagger discovery — optional `inquisitor-harness-openapi` plugin (`OpenApiAdvisor`) | ✅ done |
| 07 | Negative (oracle-calibration) scenarios | 📝 planned (`tasks/task-07`) |
| 08 | `benchmark` Gradle task (trustworthy-green) | 📝 planned (`tasks/task-08`) |
| 06 | `inquisitor-mock` + `inquisitor-mock-starter` | ⏳ reserved, not started |

## Now

- Phase 05b is green: a `@Harness` `@SpringBootTest` class with one `@Scenario`
  method per scenario reports **one sub-test per step** (19 step-tests across the
  6 demo scenarios, all PASS on the local model). Each `@Scenario` method resolves
  its markdown by name and the app's HTTP target is auto-registered from the
  random port — no per-scenario boilerplate. The standalone `ScenarioTests` is
  kept as the JUnit-free compatibility test.

## Next

- **OpenAPI discovery is in** (task-09): the optional `inquisitor-harness-openapi`
  module injects the app's spec into the system prompt via an `OpenApiAdvisor`, so
  scenarios can be written as natural-language **intent** with no endpoints (see the
  `scenarios/positive/intent` bucket + `IntentScenarioSuiteTest`). The demo serves a
  static `openapi.yaml` at `/v3/api-docs.yaml`; real consumers can use springdoc.
- **Oracle calibration** (task-07 negative scenarios) and the **`benchmark`** task
  (task-08) are planned — see `tasks/`.
- Decide on the mock-server design before implementing the `*-mock` modules.

> Keep this file current as tasks complete; move durable rationale into
> [decisions.md](decisions.md), not here.
