# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- EWP Explorer: Warn about missing files listed in the EWP and offer to remove them.
- EWP Explorer: Add context-menu actions to add/remove groups and files.
- EWP Explorer: Support drag-and-drop to move files and groups to different groups.
- Add icons for EWP files and the EWP Explorer.

### Fixed

- Refresh the EWP Explorer when `.ewp` files change on disk (for example, in CLion).

## [0.0.2] - 2026-09-17

### Added

- Add a plugin icon.
- Update the EWP when files are deleted, moved, or renamed.

### Fixed

- Do not show the "Add to EWP" dialog for files added externally (for example, by Git checkout).

## [0.0.1] - 2026-09-15

### Added

- Show the "Add to EWP" dialog when creating files.
- EWP Explorer: Show files and groups in a tree and offer to fix incorrect sorting.
