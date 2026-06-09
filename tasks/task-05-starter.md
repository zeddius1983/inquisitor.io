# Task 05 — `inquisitor-spring-boot-starter` Module

## Goal

Produce the thin starter artifact that end users declare as a single `testImplementation` dependency. No Java source — just a `build.gradle.kts` that pulls in the right transitive dependencies.

## Dependencies (declared in `build.gradle.kts`)

```kotlin
plugins {
    id("inquisitor.spring-conventions")
    id("inquisitor.publish-conventions")
}

dependencies {
    api(project(":inquisitor-autoconfigure"))
    api(libs.spring.ai.openai.spring.boot.starter)   // default provider; users can exclude + substitute
}
```

> **Note:** `api` is used (not `implementation`) so the Spring AI starter is on the consumer's compile classpath — required for property binding and Spring Boot devtools to work correctly.

## What This Starter Brings Transitively

```
inquisitor-spring-boot-starter
  ├─ inquisitor-autoconfigure
  │    ├─ inquisitor-spring-ai
  │    │    └─ inquisitor-core
  │    └─ spring-boot-autoconfigure
  └─ spring-ai-openai-spring-boot-starter
       ├─ spring-ai-core
       └─ spring-ai-openai
```

## End-User Usage

```kotlin
// consumer's build.gradle.kts
dependencies {
    testImplementation("io.inquisitor:inquisitor-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

```yaml
# src/test/resources/application.yml
inquisitor:
  base-url: http://localhost:${local.server.port}
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

## Swapping the LLM Provider

Users who want a different Spring AI backend can exclude the default OpenAI starter:
```kotlin
testImplementation("io.inquisitor:inquisitor-spring-boot-starter:0.1.0-SNAPSHOT") {
    exclude(group = "org.springframework.ai", module = "spring-ai-openai-spring-boot-starter")
}
testImplementation("org.springframework.ai:spring-ai-anthropic-spring-boot-starter")
```

## Verification

```bash
./gradlew :inquisitor-spring-boot-starter:dependencies --configuration runtimeClasspath
# Confirm full transitive tree resolves cleanly
```

## Notes / Open Questions

- `spring-ai-openai-spring-boot-starter` exact artifact coordinates — confirm against Spring AI 1.x release notes (group `org.springframework.ai`).
- Should we publish a second `inquisitor-spring-boot-starter-anthropic` variant for Anthropic/Claude out of the box? Keep it simple for now — one starter, one default provider, swap via exclusion.
- No `spring-boot-starter` base dependency needed explicitly — Spring AI's own starter already pulls it in.
