# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.0.4] - 2026-01-08

### Added
- Added PDF file type supported as input in `openccjni-cli` subcommand `pdf`
- Set OpenccConfig as single source of truth for OpenccJNI configuration.

### Changed
- Refactored `OfficeHelper` to include a core `byte[]`-based `convert()` API for in-memory document processing.
- Updated conversion result handling: introduced unified abstract `Result` base class with concrete `FileResult` and `MemoryResult` subtypes.
- Ensured backward compatibility: legacy `Result` return type remains valid and unchanged for existing users.

---

## [1.0.3] - 2025-10-23

### Changed

- Update natives to `opencc-fmmseg` v0.8.3

---

## [1.0.2] - 2025-10-04

### Added

- Added `isParallel()` and `setParallel(...)`

### Changed

- Update natives to `opencc-fmmseg` v0.8.2

### CLI

- **Java 25 compatibility**: start scripts now pass  
  `--enable-native-access=ALL-UNNAMED` by default (via `applicationDefaultJvmArgs`).  
  This silences restricted-native warnings on JDK 25+ and future-proofs JNI usage.
- **Runnable JAR**: still supported; when launching the JAR directly on JDK 25+ you must pass the flag yourself:

```bash
java --enable-native-access=ALL-UNNAMED -jar openccjava-cli-1.0.3.jar convert -c s2t -p
```

## [1.0.0] - 2025-08-31

### 📝 Changelog

- Initial stable release.
- Published to Maven Central.
- Bundled native libraries for Windows, Linux, and macOS.
- Added Javadoc and source jars.
- 