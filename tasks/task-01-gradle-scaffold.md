# Task 01 — Gradle 9 Multi-Module Build Scaffold

## Goal

Bootstrap the full multi-module Gradle 9 project layout with convention plugins, version catalog, and stub build files for every submodule. No Java source yet — only the build system.

## Key Versions

| Artifact | Version                                           |
|---|---------------------------------------------------|
| Java | 26                                                |
| Gradle wrapper | 9.5.1                                             |
| Spring Boot | 4.0.6                                             |
| Spring AI | 2.0.0-RC1                                         |
| Flexmark | 0.64.8 (I'm not sure if we ever need this at all) |
| JUnit Platform | 5.12.x                                            |
| io.spring.dependency-management plugin | 1.1.7                                             |

## Files to Create

### `settings.gradle.kts`
```kotlin
rootProject.name = "inquisitor"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://repo.spring.io/milestone")
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
    versionCatalogs {
        create("libs") { from(files("gradle/libs.versions.toml")) }
    }
}

include(
    "inquisitor-core",
    "inquisitor-spring-ai",
    "inquisitor-autoconfigure",
    "inquisitor-spring-boot-starter",
    "inquisitor-demo"
)
```

### `build.gradle.kts` (root — BOM publication, no sources)
Applies `inquisitor.publish-conventions`. Configures `java-platform` for BOM generation.

### `gradle/libs.versions.toml`
Sections: `[versions]`, `[libraries]`, `[plugins]`, `[bundles]`.

Key entries:
```toml
[versions]
spring-boot       = "4.0.0-M2"
spring-ai         = "1.0.0"
flexmark          = "0.64.8"
junit-platform    = "5.12.0"
dependency-mgmt   = "1.1.7"

[libraries]
spring-boot-dependencies  = { module = "org.springframework.boot:spring-boot-dependencies",   version.ref = "spring-boot" }
spring-ai-bom             = { module = "org.springframework.ai:spring-ai-bom",                version.ref = "spring-ai" }
flexmark-all              = { module = "com.vladsch.flexmark:flexmark-all",                   version.ref = "flexmark" }
junit-jupiter             = { module = "org.junit.jupiter:junit-jupiter",                     version.ref = "junit-platform" }

[plugins]
spring-boot              = { id = "org.springframework.boot",            version.ref = "spring-boot" }
spring-dependency-mgmt   = { id = "io.spring.dependency-management",     version.ref = "dependency-mgmt" }
```

### `build-logic/settings.gradle.kts`
```kotlin
rootProject.name = "build-logic"
```

### `build-logic/build.gradle.kts`
```kotlin
plugins { `kotlin-dsl` }
dependencies {
    implementation(libs.plugins.spring.boot.get().let { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" })
    implementation(libs.plugins.spring.dependency.mgmt.get().let { ... })
}
```

### `build-logic/src/main/kotlin/inquisitor.java-conventions.gradle.kts`
- Applies `java-library`
- `java { toolchain { languageVersion = JavaLanguageVersion.of(26) } }`
- `tasks.withType<JavaCompile> { options.compilerArgs += "--enable-preview"; options.release = 26 }`
- `tasks.withType<Test> { jvmArgs("--enable-preview"); useJUnitPlatform() }`

### `build-logic/src/main/kotlin/inquisitor.spring-conventions.gradle.kts`
- Applies `inquisitor.java-conventions`
- Applies `io.spring.dependency-management`
- Imports Spring Boot BOM + Spring AI BOM via `dependencyManagement { imports { ... } }`

### `build-logic/src/main/kotlin/inquisitor.publish-conventions.gradle.kts`
- Applies `maven-publish`
- Configures `MavenPublication` with `groupId = "io.inquisitor"`, `version` from root project

### Stub `build.gradle.kts` per submodule
Each stub applies the appropriate convention plugin:
- `inquisitor-core`: `plugins { id("inquisitor.java-conventions") }`
- `inquisitor-spring-ai`: `plugins { id("inquisitor.spring-conventions") }`
- `inquisitor-autoconfigure`: `plugins { id("inquisitor.spring-conventions") }`
- `inquisitor-spring-boot-starter`: `plugins { id("inquisitor.spring-conventions") }`
- `inquisitor-demo`: `plugins { id("inquisitor.spring-conventions"); alias(libs.plugins.spring.boot) }`

## Verification

```bash
./gradlew projects
# Expected: prints all 5 subprojects, no build errors

./gradlew dependencies --configuration runtimeClasspath -p inquisitor-demo
# Expected: Spring Boot BOM resolves cleanly from Spring milestone repo
```

## Notes / Open Questions

- Spring Boot 4 milestones may require `https://repo.spring.io/milestone`. Confirm repo URL is accessible.
- Spring AI BOM coordinates may differ between Spring AI 1.x releases — verify group ID is `org.springframework.ai`.
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` is stricter but recommended for Gradle 9; relax if a submodule requires a local file dep.
