# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/).

---

### [1.0.2-beta1]

### CLI

- **Java 25 compatibility**: start scripts now pass  
  `--enable-native-access=ALL-UNNAMED` by default (via `applicationDefaultJvmArgs`).  
  This silences restricted-native warnings on JDK 25+ and future-proofs JNI usage.
- **Runnable JAR**: still supported; when launching the JAR directly on JDK 25+ you must pass the flag yourself:

```bash
java --enable-native-access=ALL-UNNAMED -jar openccjava-cli-1.0.3.jar convert -c s2t -p
```

## [1.0.0] - 2025-08-31

## 📝 Changelog

- Initial stable release.
- Published to Maven Central.
- Bundled native libraries for Windows, Linux, and macOS.
- Added Javadoc and source jars.
- 