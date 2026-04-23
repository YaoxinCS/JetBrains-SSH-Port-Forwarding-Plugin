# Port Forwarding

[简体中文](README_ZH.md)

Port Forwarding is an IntelliJ Platform plugin for managing SSH port forwarding directly inside JetBrains IDEs.

It provides a native settings UI for defining SSH sessions and SSH tunnels, with support for both local forwarding (`-L`) and remote forwarding (`-R`).

<!-- Plugin description -->
Port Forwarding adds a native JetBrains settings page for managing SSH sessions and SSH tunnels directly inside the IDE. It supports local and remote forwarding, project-scoped sessions, auto-start, auto-reconnect, and secure credential storage through the IDE password safe.

Homepage: [GitHub](https://github.com/YaoxinCS/JetBrains-SSH-Port-Forwarding-Plugin)
<!-- Plugin description end -->

## Features

- Manage SSH sessions in `Settings | Tools | Port Forwarding`
- Create and edit SSH tunnels for:
  - Local -> Remote (`-L`)
  - Remote -> Local (`-R`)
- Start or stop individual tunnels
- Start or stop all tunnels for a session
- Mark sessions as global or project-scoped
- Enable per-tunnel auto-start and auto-reconnect
- Test SSH connections before saving
- Use password, key pair, or OpenSSH agent authentication
- Store passwords and passphrases in the IDE password safe
- Follow the IDE language automatically via built-in localization support

## Requirements

- A JetBrains IDE based on the IntelliJ Platform
- IntelliJ Platform build `252+`
- An available SSH server
- A system `ssh` client when using OpenSSH agent mode

## Installation

### Install from disk

1. Download a plugin ZIP from the repository releases, or build it locally.
2. In your IDE, open `Settings/Preferences | Plugins`.
3. Click the gear icon.
4. Choose `Install Plugin from Disk...`.
5. Select the plugin ZIP and restart the IDE.

### Build locally

```powershell
./gradlew.bat buildPlugin
```

The build artifact will be generated in:

```text
build/distributions/Port Forwarding-<version>.zip
```

## Usage

1. Open `Settings | Tools | Port Forwarding`.
2. Create an SSH session.
3. Add one or more tunnels in `Local` or `Remote` mode.
4. Start a single tunnel or all tunnels in the selected session.
5. Optionally enable:
   - `Visible only in this project`
   - `Auto start when the IDE launches`
   - `Auto reconnect after disconnect`

## Authentication

The plugin currently supports:

- Password authentication
- Key pair authentication
- OpenSSH agent / OpenSSH config based authentication

## Project Scope

Sessions can be stored in two scopes:

- Global: visible across the IDE
- Project-only: visible only in the current project

Project-only sessions are stored in project-level settings and can participate in project startup behavior.

## Development

Useful tasks:

```powershell
./gradlew.bat buildPlugin
./gradlew.bat test
```

## Contributing

Issues and pull requests are welcome.

If you plan to make a larger change, open an issue first so the scope and direction can be aligned before implementation.

## License

This project is licensed under the terms of the [MIT License](LICENSE).
