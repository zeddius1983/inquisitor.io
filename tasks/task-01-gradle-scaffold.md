# Task 01 — Gradle 9 Multi-Module Build Scaffold

## Goal

Bootstrap the full multi-module Gradle 9 project layout with convention plugins, version catalog, and stub build files for every submodule. No Java source yet — only the build system.

## Key Versions

| Artifact | Version |
|---|---|
| Java | 26 |
| Gradle wrapper | 9.5.1 |
| Spring Boot | 4.0.6 |
| Spring AI | 2.0.0-RC1 |
| Flexmark | 0.64.8 |
| JUnit Jupiter | 5.12.x |
| io.spring.dependency-management plugin | 1.1.7 |
| Lombok | 1.18.x |

## Files to Create

### `settings.gradle.kts`
```kotlin
rootProject.name = "inquisitor.io"

pluginManagement {
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
    "inquisitor-demo",
    "inquisitor-junit5-extension"
)
```

> No `includeBuild("buildSrc")` needed — Gradle auto-discovers `buildSrc/`.

### `build.gradle.kts` (root — BOM publication, no sources)
Applies `inquisitor.publish-conventions`. Configures `java-platform` for BOM generation. Keep minimal.

### `gradle/libs.versions.toml`
Sections: `[versions]`, `[libraries]`, `[plugins]`.

```toml
[versions]
spring-boot      = "4.0.6"
spring-ai        = "2.0.0-RC1"
flexmark         = "0.64.8"
junit-jupiter    = "5.12.x"
dependency-mgmt  = "1.1.7"
lombok           = "1.18.x"

[libraries]
spring-boot-dependencies = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
spring-ai-bom            = { module = "org.springframework.ai:spring-ai-bom",              version.ref = "spring-ai" }
flexmark-all             = { module = "com.vladsch.flexmark:flexmark-all",                 version.ref = "flexmark" }
junit-jupiter            = { module = "org.junit.jupiter:junit-jupiter",                   version.ref = "junit-jupiter" }
lombok                   = { module = "org.projectlombok:lombok",                          version.ref = "lombok" }

[plugins]
spring-boot            = { id = "org.springframework.boot",         version.ref = "spring-boot" }
spring-dependency-mgmt = { id = "io.spring.dependency-management",  version.ref = "dependency-mgmt" }
```

### `buildSrc/settings.gradle.kts`
```kotlin
rootProject.name = "buildSrc"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.spring.io/milestone")
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
```

> Shares the root version catalog with convention plugin scripts via the relative path.

### `buildSrc/build.gradle.kts`
```kotlin
plugins { `kotlin-dsl` }

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.6")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}
```

> Plugin dependencies are hardcoded here (not catalog refs) — this is the build tooling layer, and the catalog is not yet resolved when these deps are declared.

### `buildSrc/src/main/kotlin/inquisitor.java-conventions.gradle.kts`
- Applies `java-library`
- `java { toolchain { languageVersion = JavaLanguageVersion.of(26) } }`
- `tasks.withType<JavaCompile> { options.compilerArgs += "--enable-preview"; options.release = 26 }`
- `tasks.withType<Test> { jvmArgs("--enable-preview"); useJUnitPlatform() }`

### `buildSrc/src/main/kotlin/inquisitor.spring-conventions.gradle.kts`
- Applies `inquisitor.java-conventions`
- Applies `io.spring.dependency-management`
- Imports Spring Boot BOM via `SpringBootPlugin.BOM_COORDINATES` and Spring AI BOM:
```kotlin
dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-RC1")
    }
}
```

### `buildSrc/src/main/kotlin/inquisitor.publish-conventions.gradle.kts`
- Applies `maven-publish`
- Configures `MavenPublication` with `groupId = "io.inquisitor"`, `version` from root project

### Stub `build.gradle.kts` per submodule
Each stub applies the appropriate convention plugin:
- `inquisitor-core`: `plugins { id("inquisitor.java-conventions") }`
- `inquisitor-spring-ai`: `plugins { id("inquisitor.spring-conventions") }`
- `inquisitor-autoconfigure`: `plugins { id("inquisitor.spring-conventions") }`
- `inquisitor-spring-boot-starter`: `plugins { id("inquisitor.spring-conventions") }`
- `inquisitor-demo`: `plugins { id("inquisitor.spring-conventions"); alias(libs.plugins.spring.boot) }`
- `inquisitor-junit5-extension`: `plugins { id("inquisitor.java-conventions") }`

## Verification

```bash
./gradlew projects
# Expected: prints all 6 subprojects, no build errors

./gradlew dependencies --configuration runtimeClasspath -p inquisitor-demo
# Expected: Spring Boot BOM resolves cleanly from Spring milestone repo
```

## Notes

- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` is stricter but recommended for Gradle 9; relax if a submodule requires a local file dep.
- Spring AI BOM coordinates confirmed: `org.springframework.ai:spring-ai-bom`.
- Spring Boot BOM is imported via `SpringBootPlugin.BOM_COORDINATES` in the convention plugin — avoids hardcoding coordinates and version in two places.
- Flexmark is included in the catalog but may be removed if no module ends up needing it.
- Lombok is declared as a library entry only; individual modules that need it add it via `dependencies { compileOnly(libs.lombok); annotationProcessor(libs.lombok) }`.
