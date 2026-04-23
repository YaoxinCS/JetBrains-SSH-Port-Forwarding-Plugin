# Changelog

## [0.1.1] - 2026-04-23

### Fixed

- Store SSH session and tunnel definitions in plugin-managed XML files instead of IntelliJ persistent state files.
- Always save SSH passwords and key passphrases through Password Safe and remove the save-secret checkboxes.

## [0.1.0] - 2026-04-23

### Added

- Add a native `Settings | Tools | Port Forwarding` page for managing SSH sessions and tunnels.
- Support local forwarding (`-L`) and remote forwarding (`-R`).
- Add project-scoped sessions, tunnel auto-start, auto-reconnect, and manual start/stop controls.
- Store passwords and key passphrases through the IDE password safe.
- Add English and Simplified Chinese UI resources.
- Add plugin icons and toolbar action icons.

### Fixed

- Keep tunnel runtime status visible in the settings UI after start/stop changes.
- Replace internal IntelliJ startup API usage with the public project startup activity.
- Persist session and tunnel edits immediately without blocking the settings dialog.
