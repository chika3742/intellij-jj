# intellij-jj

Jujutsu VCS integration plugin for JetBrains IDEs.

## Features

This plugin provides comprehensive support for [Jujutsu](https://github.com/martinvonz/jj) (jj), a Git-compatible version control system, within JetBrains IDEs.

### Implemented Features

- **VCS Integration**: Full integration with JetBrains IDE's VCS system
- **Change Log Viewer**: View your change history with `jj log`
- **Create New Changes**: Create new changes with `jj new`
- **Edit Changes**: Switch to and edit different changes with `jj edit`
- **Split Changes**: Split a change into multiple changes with `jj split`
- **Squash Changes**: Squash changes together with `jj squash`
- **Bookmark Management**: Create and list bookmarks

## Requirements

- JetBrains IDE (IntelliJ IDEA, PyCharm, WebStorm, etc.) version 2023.2 or later
- Jujutsu CLI (`jj`) installed and available in PATH

## Installation

### From Plugin Marketplace (Future)

1. Open IDE Settings/Preferences
2. Go to Plugins
3. Search for "Jujutsu"
4. Click Install

### From Source

1. Clone this repository
2. Run `./gradlew buildPlugin`
3. Install the plugin from disk: Settings → Plugins → Install Plugin from Disk → Select `build/distributions/intellij-jj-*.zip`

## Usage

1. Open a project that uses Jujutsu VCS (contains a `.jj` directory)
2. The IDE will automatically detect Jujutsu as the VCS
3. Access Jujutsu commands from the **VCS → Jujutsu** menu

### Available Actions

- **New Change**: Create a new change (optionally with a description)
- **Edit Change**: Switch to a different change by its ID
- **Split Change**: Split the current change into multiple changes
- **Squash Change**: Squash the current change into its parent
- **Show Log**: View the change history
- **Create Bookmark**: Create a new bookmark at the current change
- **List Bookmarks**: View all bookmarks

## Building

```bash
./gradlew buildPlugin
```

## Development

```bash
# Run the plugin in a sandboxed IDE
./gradlew runIde

# Run tests
./gradlew test
```

## License

See [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.