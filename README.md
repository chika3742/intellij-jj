# intellij-jj

Jujutsu (`jj`) VCS integration plugin for JetBrains IDEs.

## Status

This project is an active work in progress.
This README reflects the current implementation in this repository.

## Implemented

- Detects Jujutsu repositories via `.jj` directories (`JujutsuVcsRootChecker`).
- Registers Jujutsu as a VCS (`JujutsuVcs`).
- Populates the Changes view from `jj diff --summary -r @` (`JujutsuChangeProvider`).
- Provides file revision content for diff operations using `jj file show` (`JujutsuDiffProvider`).
- Supports commit from IntelliJ Commit UI with `jj commit` (`JujutsuCheckinEnvironment` and `actions/JjCommitAction`).
- Prefills and updates commit message from working-copy description (`JujutsuCommitMessageProvider`).
- Integrates with VCS Log (`log/*`):
  - metadata loading
  - full commit details with changed files
  - bookmark refs
  - current working-copy change highlighting
  - filtering by text/hash/user/date/path
- Refreshes VCS/Log data when `.jj` metadata changes are detected (`repo/JujutsuRepositoryWatcher`).

## Not Implemented Yet / Known Gaps

- `Abandon Change` action is present but not implemented (`actions/JjAbandon.kt`).
- No plugin settings UI yet (`JujutsuConfigurableProvider` returns `null`).
- `jj` executable path/options are not configurable (executor calls `jj` from `PATH`).
- `JujutsuVcs` does not yet provide rollback/history/update/integrate environments.
- `Revset.bookmarks()` does not yet support bookmark pattern matching.
- `plugin.xml` description includes several planned actions (`jj new/edit/split/squash/merge`, bookmark management) that are not implemented in this codebase yet.

## Requirements

- JetBrains IDE based on IntelliJ Platform `2025.3+` (since build `253`)
- Jujutsu CLI (`jj`) installed and available in `PATH`
- Java 21 (development toolchain)

## Build and Run

```bash
# Build plugin ZIP
./gradlew buildPlugin

# Run plugin in sandbox IDE
./gradlew runIde

# Run tests
./gradlew test

# Validate plugin configuration
./gradlew verifyPluginProjectConfiguration
```

Built plugin archive:

- `build/distributions/intellij-jj-*.zip`

Install via **Settings/Preferences -> Plugins -> Install Plugin from Disk**.

## Testing Notes

- Current tests are limited (`src/test/kotlin/net/chikach/intellijjj/JujutsuSerializeTest.kt`).
- Tests invoke `jj` directly and assume `jj` is available in your environment.

## Project Layout

```text
src/main/kotlin/net/chikach/intellijjj/
  JujutsuVcs.kt
  JujutsuVcsRootChecker.kt
  JujutsuChangeProvider.kt
  JujutsuDiffProvider.kt
  JujutsuConfigurableProvider.kt
  actions/
  commit/
  jujutsu/
  log/
  repo/

src/main/resources/META-INF/plugin.xml
```

## Notes for Contributors

- Keep `jj` command execution centralized in `jujutsu/JujutsuCommandExecutor.kt`.
- When changing VCS integration behavior, confirm IntelliJ API contracts from JavaDoc/source (threading/lifecycle/refresh semantics), not memory.
- If README and code diverge, treat code as source of truth and update README accordingly.

## License

See [LICENSE](LICENSE).
