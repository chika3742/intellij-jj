# AGENTS.md

This file defines repository-specific guidance for coding agents working in this project.

## Project Summary

- Project: `intellij-jj`
- Type: JetBrains IDE plugin (Kotlin + IntelliJ Platform Gradle Plugin)
- Goal: Integrate Jujutsu (`jj`) as a VCS inside JetBrains IDEs
- Java/Kotlin target: 21
- IntelliJ target: 2025.3.2 (since build `253`)

## Source of Truth

- Trust code and plugin registration first:
  - `src/main/resources/META-INF/plugin.xml`
  - `src/main/kotlin/net/chikach/intellijjj/**`
- Treat `README.md` as secondary context. If README and code conflict, follow code.

## Critical Rule for IntelliJ VCS API Work

Information about writing IntelliJ VCS plugins is limited and can be incomplete in high-level docs.

- Do not rely on memory alone for VCS extension behavior.
- For every non-trivial VCS-related change, verify against current JavaDoc and platform source for the exact API you touch (threading, lifecycle, nullability, refresh contracts, etc.).
- Prefer primary sources each time:
  - IntelliJ Platform SDK docs
  - JavaDoc/KDoc on the API in use
  - IntelliJ platform source implementation for the same interface/class

If there is ambiguity, pause assumptions and confirm from JavaDoc/source before coding.

## Current Architecture (Quick Map)

- VCS entry point: `JujutsuVcs`
- Root detection: `JujutsuVcsRootChecker` (`.jj` directory)
- Change detection: `JujutsuChangeProvider`
- Diff/content: `JujutsuDiffProvider`
- Commit integration:
  - `JujutsuCheckinEnvironment`
  - `JujutsuCommitMessageProvider`
- Log integration: `log/JujutsuVcsLogProvider` and related classes
- Repository refresh/watch:
  - `repo/JujutsuRepositoryWatcher`
  - `repo/JujutsuRepositoryChangeListener`
- CLI boundary for `jj` execution:
  - `jujutsu/JujutsuCommandExecutor`
  - `jujutsu/commands/*`

## Working Rules

- Keep `jj` process execution centralized in `JujutsuCommandExecutor` unless there is a clear reason not to.
- Register new VCS/UI contributions in `plugin.xml` and ensure implementation classes are wired consistently.
- Prefer background-thread-safe behavior for long operations; do not block EDT unless API explicitly requires it.
- Preserve existing error handling patterns (`VcsException`, IntelliJ logger warnings/errors).

## Build and Verification

Use Gradle wrapper:

- `./gradlew buildPlugin`
- `./gradlew test`
- `./gradlew runIde`

Notes:

- Some tests and runtime flows require `jj` installed and available in `PATH`.
- When changing parsing/serialization/command output handling, add or update focused tests under `src/test/kotlin`.

## Change Checklist

Before finishing, verify:

- Compiles successfully.
- `plugin.xml` registrations match implemented classes.
- VCS/log/commit changes respect API contracts confirmed via JavaDoc/source.
- Root refresh behavior is still triggered correctly after repository metadata changes.
