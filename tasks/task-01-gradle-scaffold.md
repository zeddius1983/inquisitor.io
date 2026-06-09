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
| JUnit Jupiter | 5.12.2 |
| io.spring.dependency-management plugin | 1.1.7 |
| Lombok | 1.18.36 |

## Files to Create

### `gradle.properties`
```properties
group=io.inquisitor

org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.configuration-cache=true
org.gradle.caching=true
```

> `group` here propagates to all subprojects automatically. Configuration cache and parallel execution are stable in Gradle 9 and cut CI time significantly.

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
    "inquisitor-harness",
    "inquisitor-harness-starter",
    "inquisitor-harness-junit",
    "inquisitor-harness-junit-starter",
    "inquisitor-mock",
    "inquisitor-mock-starter",
    "inquisitor-demo"
)
```

> No `includeBuild("buildSrc")` needed — Gradle auto-discovers `buildSrc/`.

### `build.gradle.kts` (root — BOM only, no sources)
```kotlin
plugins {
    `java-platform`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            artifactId = "inquisitor-bom"
        }
    }
}
```

> `java-platform` and `java-library` are mutually exclusive. The root BOM does **not** apply `inquisitor.publish-conventions` (which is for Java modules). Maven publish is configured directly here instead.

### `gradle/libs.versions.toml`
```toml
[versions]
spring-boot      = "4.0.6"
spring-ai        = "2.0.0-RC1"
flexmark         = "0.64.8"
junit-jupiter    = "5.12.2"
dependency-mgmt  = "1.1.7"
lombok           = "1.18.36"

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

> Wildcard versions (`5.12.x`) are not valid TOML — Gradle will fail to parse the catalog. Always pin exact versions.

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

> Shares the root version catalog with convention plugin scripts via relative path.

### `buildSrc/build.gradle.kts`
```kotlin
plugins { `kotlin-dsl` }

dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.6")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    // Expose version catalog type-safe accessors to convention plugin scripts
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
```

> Plugin dependencies are hardcoded (not catalog refs) — the catalog is not yet resolved when these deps are declared. The third `implementation` line exposes generated catalog accessors so convention scripts can read versions via `libs.versions.*`.

### `buildSrc/src/main/kotlin/inquisitor.java-conventions.gradle.kts`
```kotlin
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs += "--enable-preview"
    options.release = 26
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
    useJUnitPlatform()
}
```

### `buildSrc/src/main/kotlin/inquisitor.spring-conventions.gradle.kts`
```kotlin
plugins {
    id("inquisitor.java-conventions")
    id("io.spring.dependency-management")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        mavenBom(libs.spring.ai.bom.get().toString())
    }
}
```

> Spring Boot BOM is imported via `SpringBootPlugin.BOM_COORDINATES` to avoid duplicating coordinates and version. Spring AI BOM version is sourced from the catalog via the type-safe accessor — no hardcoded version string.

### `buildSrc/src/main/kotlin/inquisitor.publish-conventions.gradle.kts`
```kotlin
plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = project.group.toString()
            version = project.version.toString()
        }
    }
}
```

> This convention applies only to Java modules (`java` component). It must **not** apply `java-library` — that is handled by `inquisitor.java-conventions`. The root BOM configures its own publication directly.

### Stub `build.gradle.kts` per submodule

| Module | Plugins |
|---|---|
| `inquisitor-harness` | `id("inquisitor.spring-conventions")` |
| `inquisitor-harness-starter` | `id("inquisitor.spring-conventions")` |
| `inquisitor-harness-junit` | `id("inquisitor.spring-conventions")` |
| `inquisitor-harness-junit-starter` | `id("inquisitor.spring-conventions")` |
| `inquisitor-mock` | `id("inquisitor.java-conventions")` |
| `inquisitor-mock-starter` | `id("inquisitor.spring-conventions")` |
| `inquisitor-demo` | `id("inquisitor.spring-conventions")` + `alias(libs.plugins.spring.boot)` |

Each stub file is intentionally empty aside from the `plugins {}` block.

## Verification

```bash
./gradlew projects
# Expected: prints all 6 subprojects, no build errors

./gradlew dependencies --configuration runtimeClasspath -p inquisitor-demo
# Expected: Spring Boot BOM resolves cleanly from Spring milestone repo

./gradlew --configuration-cache help
# Expected: "Configuration cache entry stored" on first run, "reused" on second
```

## Notes

- `RepositoriesMode.FAIL_ON_PROJECT_REPOS` is stricter but recommended for Gradle 9; relax if a submodule requires a local file dep.
- `group=io.inquisitor` in `gradle.properties` propagates to all subprojects; no need to set it in individual `build.gradle.kts` files.
- Flexmark is in the catalog but may be removed if no module uses it.
- Lombok is declared as a library entry only; modules that need it add: `compileOnly(libs.lombok)` + `annotationProcessor(libs.lombok)`.
- The type-safe catalog accessor trick (`libs.javaClass.superclass...`) is a known Gradle workaround for sharing catalog accessors into `buildSrc`. It is stable as of Gradle 8.x and supported in Gradle 9.
