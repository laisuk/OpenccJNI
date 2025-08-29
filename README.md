# OpenccJNI — Java JNI wrapper for openccjni-fmmseg

> High-performance Simplified/Traditional Chinese conversion using OpenCC with FMMSEG longest-match segmentation — from
> Java, via JNI.

[![Build](https://img.shields.io/badge/build-gradle-green)](https://gradle.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-win%20%7C%20linux%20%7C%20macOS-informational)](#-supported-platforms)
<!-- TODO: add real CI + release badges once ready -->

---

## ✨ Features

- **OpenCC + FMMSEG**: Longest-match phrase segmentation backed by OpenCC dictionaries.
- **Fast & parallel-friendly**: Designed to be used safely across threads (see ThreadLocal note).
- **Auto native load**: `OpenCC` automatically loads the native library (system-first, then from resources) — you
  typically **do not need** to call `NativeLibLoader` yourself.
- **Wide config support**: Common OpenCC configurations out of the box (see below).

> This repository targets **openccjni-fmmseg-capi** only. Jieba JNI will be provided in a separate repo later.

---

## 📦 Installation

- **A — JAR with embedded natives (recommended)**  
  Embed natives under:  
  `openccjni/natives/{os}-{arch}/{System.mapLibraryName("OpenccWrapper")}`  
  The `OpenCC` class will attempt `System.loadLibrary` first, then auto-extract from resources to a shared temp dir and
  `System.load()` it.

- **B — System-installed natives**  
  Put `OpenccWrapper.dll` / `libOpenccWrapper.so` / `libOpenccWrapper.dylib` on your `PATH` / `LD_LIBRARY_PATH` /
  `DYLD_LIBRARY_PATH`.

> Maven Central/Gradle coordinates will be added when published. For now, use a local or GitHub release JAR.

---

## 🚀 Quick Start (auto native load)

```java
import openccjni.OpenCC;  // High-level, autoloads native on first use

public class Demo {
    public static void main(String[] args) {
        // OpenCC autoloads the native library internally.
        // No need to call NativeLibLoader explicitly.

        // Create an instance bound to a specific config:
        OpenCC cc = OpenCC.fromConfig("s2t");

        String input = "汉字转换测试";
        String output = cc.convert(input);

        System.out.println(output);  // 漢字轉換測試
    }
}
```

### Supported config names

```
s2t, t2s, s2tw, tw2s, s2twp, tw2sp,
s2hk, hk2s, t2tw, t2twp, t2hk,
tw2t, tw2tp, hk2t, t2jp, jp2t
```

> Internally, `OpenCC` typically uses a thread-local `OpenccWrapper` instance, so concurrent conversions are safe
> without extra locking in user code.

---

## 🧩 Native loading details

Default resolution order used by `OpenCC`:

1. `System.loadLibrary("OpenccWrapper")` (PATH / `java.library.path` / working dir, etc.)
2. If not found, extract from JAR resources under:
   ```
   /openccjni/natives/{os}-{arch}/{System.mapLibraryName("OpenccWrapper")}
   ```
   Examples:
   ```
   /openccjni/natives/windows-x86_64/OpenccWrapper.dll
   /openccjni/natives/linux-x86_64/libOpenccWrapper.so
   /openccjni/natives/macos-aarch64/libOpenccWrapper.dylib
   ```

`NativeLibLoader` is still available for advanced/manual loading scenarios, but **not required** for typical usage.

---

## 🔧 Build from source

### Prerequisites

- **JDK**: 8+ (11+ recommended)
- **C/C++ toolchain**:
    - **Windows**: MinGW-w64 (posix-seh, e.g., x86_64-14.x). 32-bit builds via `i686` toolchain.
    - **Linux**: GCC/Clang with standard libc.
    - **macOS**: Xcode/Clang.
- **Native dependency**: `openccjni-fmmseg-capi` (build or provide binaries on your link path).

### Generate JNI header

```bash
# From the Java source root; outputs a JNI header for OpenccWrapper bindings
javac -h . src/main/java/openccjni/OpenccWrapper.java
```

This produces a header like `opencc_OpenccWrapper.h` that your C/C++ file includes.

### Example link commands

> Adjust include/lib paths for your environment. Library name is illustrative.

**Windows (MinGW-w64, x64)**

```bat
g++ -shared -O2 -std=c++17 -o OpenccWrapper.dll OpenccWrapper.cpp ^
  -I . ^
  -I "C:\Java\zulu17\include" -I "C:\Java\zulu17\include\win32" ^
  -L . -lopencc_fmmseg_capi
```

**Linux (x86_64)**

```bash
g++ -shared -fPIC -O2 -std=c++17 -o libOpenccWrapper.so OpenccWrapper.cpp   -I . -I "${JAVA_HOME}/include" -I "${JAVA_HOME}/include/linux"   -L . -lopencc_fmmseg_capi
```

**macOS (arm64/x86_64)**

```bash
clang++ -shared -fPIC -O2 -std=c++17 -o libOpenccWrapper.dylib OpenccWrapper.cpp   -I . -I "${JAVA_HOME}/include" -I "${JAVA_HOME}/include/darwin"   -L . -lopencc_fmmseg_capi
```

Place the resulting native next to the JAR or embed it under `/openccjni/natives/{os}-{arch}/...` in the JAR.

---

## 🧵 Threading & performance

- Use `OpenCC` instances freely across threads; the high-level API keeps per-thread JNI handles under the hood for
  lock-free conversions.
- The native library relies on immutable data and minimal synchronization, so parallel conversions are efficient.
- Prefer reusing per-thread instances rather than recreating for every call.

---

## 🖥️ Supported platforms

| OS      | Arch          | Notes                                  |
|---------|---------------|----------------------------------------|
| Windows | x86_64, x86*  | Win7+ works with compatible toolchains |
| Linux   | x86_64        | glibc-based                            |
| macOS   | arm64, x86_64 | Modern macOS with Clang toolchain      |

\* 32-bit Windows builds are optional; use `i686` MinGW-w64 and a matching JDK.

---

## 🔜 Roadmap

- Separate **OpenccJiebaJNI** (segmentation/keywords) as its own repo.
- Publish to **Maven Central** with platform classifiers.
- Add CI matrices for all OS/arch targets and attach binaries to GitHub Releases.

---

## 📁 Project layout (key files)

```
openccjni/
├─ src/main/java/openccjni/
│  ├─ OpenCC.java             # High-level Java API (auto native load, configs)
│  ├─ OpenccWrapper.java      # Low-level JNI bridge class (native methods)
│  └─ NativeLibLoader.java    # Optional: manual/system-first loader helper
├─ src/main/resources/openccjni/natives/   # Optional: embedded natives
│  └─ {os}-{arch}/{mapped-lib-name}
└─ ...
```

---

## 📝 License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE).

**Acknowledgements**

- OpenCC and related dictionary projects.
- FMMSEG longest-match segmentation.
- `openccjni-fmmseg-capi`, the native library that powers the JNI bridge.

---

## ❓ FAQ

**Q: Do I need to call `NativeLibLoader`?**  
A: No. `OpenCC` auto-loads the native library (system-first, then resource extract). `NativeLibLoader` remains available
for advanced cases.

**Q: Can I call it from multiple threads?**  
A: Yes. The high-level API maintains per-thread JNI contexts, so parallel conversions are safe and efficient.

**Q: Where do I put the native DLL/SO/DYLIB if not embedding?**  
A: On your system library path (`PATH`, `LD_LIBRARY_PATH`, `DYLD_LIBRARY_PATH`), so `System.loadLibrary` can find it.
