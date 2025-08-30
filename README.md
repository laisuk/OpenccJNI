# OpenccJNI: Java JNI wrapper for opencc-fmmseg

[![Maven Central](https://img.shields.io/maven-central/v/io.github.laisuk/openccjni.svg)](https://central.sonatype.com/artifact/io.github.laisuk/openccjni)
[![javadoc](https://javadoc.io/badge2/io.github.laisuk/openccjni/javadoc.svg)](https://javadoc.io/doc/io.github.laisuk/openccjni)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-win%20%7C%20linux%20%7C%20macOS-informational)](#-supported-platforms)

> High-performance Simplified/Traditional Chinese conversion using OpenCC with FMMSEG longest-match segmentation — from
> Java, via JNI.

---

## ✨ Features

- **OpenCC + FMMSEG**: Longest-match phrase segmentation backed by OpenCC dictionaries.
- **Fast & parallel-friendly**: Designed to be used safely across threads (see ThreadLocal note).
- **Auto native load**: `OpenCC` automatically loads the native library (system-first, then from resources) — you
  typically **do not need** to call `NativeLibLoader` yourself.
- **Wide config support**: Common OpenCC configurations out of the box (see below).

> This repository targets [**opencc-fmmseg-capi**](https://github.com/laisuk/opencc-fmmseg) only. Jieba JNI will be
> provided in a separate repo later.

---

## Getting Started

`openccjni` is a small Java JNI wrapper around OpenCC + FMM segmentation for high-quality Simplified/Traditional Chinese
conversion. It ships with a Java API and loads a native library at runtime.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("io.github.laisuk:openccjni:1.0.0")
}
```

### Maven

```xml

<dependency>
    <groupId>io.github.laisuk</groupId>
    <artifactId>openccjni</artifactId>
    <version>1.0.0</version>
</dependency>
```

> Requires Java 8+

## Quick start

### A) One-liner conversion (static helper)

```java
import opencc.OpenCC;

public class Demo {
    static void main(String[] args) {
        String text = "汉字转换测试";
        String out = OpenCC.convert(text, "s2t"); // Simplified → Traditional
        System.out.println(out);
    }
}
```

### B) Instance usage (explicit wrapper)

If you prefer managing the native handle explicitly:

```java
import opencc.OpenccWrapper;

public class Demo {
    static void main(String[] args) {
        try (OpenccWrapper w = new OpenccWrapper()) {
            String out = w.convert("汉字转换测试", "s2t");
            System.out.println(out);
        }
    }
}
```

Both styles are thread-safe: the static helper uses a ThreadLocal native wrapper under the hood; the instance style is
safe to reuse across calls and auto-closes.

### Supported config names

```
s2t, t2s, s2tw, tw2s, s2twp, tw2sp,
s2hk, hk2s, t2tw, t2twp, t2hk,
tw2t, tw2tp, hk2t, t2jp, jp2t
```

Example:

```text
OpenCC.convert("後臺處理程序", "t2s"); // → "后台处理程序"

```

---

## 📦 Library Installation

- **A — JAR with embedded natives (recommended)**  
  Embed natives under:  
  `openccjni/natives/{os}-{arch}/{System.mapLibraryName("OpenccWrapper")}` and,  
  `openccjni/natives/{os}-{arch}/{System.mapLibraryName("opencc_fmmseg_capi")}`  
  The `OpenCC` class will attempt `System.loadLibrary` first, then auto-extract from resources to a shared temp dir and
  `System.load()` it.

- **B — System-installed natives**  
  Put `OpenccWrapper.dll` + `opencc_fmmseg_capi.dll`/ `libOpenccWrapper.so` + `libopencc_fmmseg_capi.so` /
  `libOpenccWrapper.dylib` + `libopencc_fmmseg_capi.dylib` on your `PATH` / `LD_LIBRARY_PATH` /
  `DYLD_LIBRARY_PATH`.

> Maven Central/Gradle coordinates will be added when published. For now, use a local or GitHub release JAR.

---

## 🚀 Quick Start (auto native load)

```java
import openccjni.OpenCC;  // High-level, autoloads native on first use

public class Demo {
    static void main(String[] args) {
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
- For long-running servers, prefer reusing the instance (pattern B) to amortize initialization.

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

## 📖 API Reference

The main entry point is the [`OpenCC`](openccjni/src/main/java/openccjni/OpenCC.java) class.  
It provides both **static one-off helpers** and an **instance API** with a persistent conversion profile.

### Static Methods

```text
// Create a new instance with the given config (default: "s2t")
OpenCC.fromConfig(String config)

// One-off conversion
OpenCC.convert(String input, String config)
OpenCC.convert(String input, String config, boolean punctuation)

// Check for Traditional/Simplified Chinese characters
// Returns: 1 - Traditional, 2 - Simplified, 0 - Others
OpenCC.zhoCheck(String text) -> int

// Supported config keys
OpenCC.getSupportedConfigs() -> List<String>

// Last error message
OpenCC.getLastError() -> String

```

### Instance Methods

```text
// Constructors
new OpenCC()                   // defaults to "s2t"
new OpenCC(String config)      // uses given config (fallback to "s2t" if invalid)

// Conversion
cc.convert(String input)
cc.convert(String input, boolean punctuation)

// Config management
cc.getConfig() -> String
cc.setConfig(String config)

// Error handling
cc.getLastError() -> String
cc.setLastError(String err)

```

### Supported Configurations

The following configuration keys are recognized (matching OpenCC profiles
):

- `s2t` – Simplified → Traditional
- `t2s` – Traditional → Simplified
- `s2tw`, `tw2s`, `s2twp`, `tw2sp` – Taiwan variants
- `s2hk`, `hk2s` – Hong Kong variants
- `t2tw`, `tw2t`, `t2twp`, `tw2tp` – Traditional ↔ Taiwan
- `t2hk`, `hk2t` – Traditional ↔ Hong Kong
- `t2jp`, `jp2t` – Traditional ↔ Japanese

### Usage Examples

```java
// Static one-off conversion
String out = OpenCC.convert("汉字", "s2t");  // 漢字

// Instance API with persistent config
OpenCC cc = OpenCC.fromConfig("tw2s");
String out = cc.convert("繁體字");  // 繁体字

// With punctuation conversion
String out = OpenCC.convert("“汉字”", "s2t", true);  // 「漢字」

```

---

## `openccjni-cli`

Command-line tool based on `OpenccJNI`.

#### Build

```bash
./gradlew distZip
```

Zip file will be created in: `openccjni-cli/build/distributions/openccjni-cli-<version>.zip`

#### Run (after extracting)

```bash
bin/openccjni-cli.bat convert -c s2t -i input.txt -o output.txt
```

### Plain Text Conversion:

```bash
bin/openccjni-cli convert --help                                                           
Usage: openccjni-cli convert [-hpV] [--list-configs] -c=<conversion>
                             [--con-enc=<encoding>] [-i=<file>]
                             [--in-enc=<encoding>] [-o=<file>]
                             [--out-enc=<encoding>]
Convert plain text using OpenccJNI
  -c, --config=<conversion>  Conversion configuration
      --con-enc=<encoding>   Console encoding for interactive mode. Ignored if
                               not attached to a terminal. Common <encoding>:
                               UTF-8, GBK, Big5
  -h, --help                 Show this help message and exit.
  -i, --input=<file>         Input file
      --in-enc=<encoding>    Input encoding
      --list-configs         List all supported OpenccJNI conversion
                               configurations
  -o, --output=<file>        Output file
      --out-enc=<encoding>   Output encoding
  -p, --punct                Punctuation conversion (default: false)
  -V, --version              Print version information and exit.
```

### Office Document Conversion:

Supported Office document formats: `.docx`, `.xlsx`, `.pptx`, `.odt`, `.ods`, `.odp`, `.epub`

```bash
bin/openccjni-cli.bat office -c s2t -i book.docx -o book_converted.docx
```

```bash
bin/openccjni-cli office --help 
Usage: opencccli office [-hpV] [--auto-ext] [--[no-]keep-font] -c=<conversion>
                        [--format=<format>] -i=<file> [-o=<file>]
Convert Office documents using OpenccJNI
      --auto-ext          Auto-append extension to output file
  -c, --config=<conversion>
                          Conversion configuration
      --format=<format>   Target Office format (e.g., docx, xlsx, pptx, odt,
                            epub)
  -h, --help              Show this help message and exit.
  -i, --input=<file>      Input Office file
      --[no-]keep-font    Preserve font-family info (default: false)
  -o, --output=<file>     Output Office file
  -p, --punct             Punctuation conversion (default: false)
  -V, --version           Print version information and exit.
```

#### Optional flags:

- `--punct`: Enable punctuation conversion.
- `--auto-ext`: Auto-append extension like `_converted`.
- `--keep-font` / `--no-keep-font`: Preserve original fonts (Office only).
- `--in-enc` / `--out-enc`: Specify encoding (e.g. `GBK`, `BIG5`, `UTF-8`).
- `--format`: Force document format (e.g., `docx`, `epub`).
- `--list-configs`: Show supported OpenCC configs.

---

## 🧾 Encodings (Charsets)

- **Linux/macOS**: Terminals are UTF-8 by default. You usually don’t need to set anything.
- **Windows**: The console isn’t always UTF-8. If you’re piping or using non-UTF-8 files, set encodings explicitly.

### CLI flags (recommended)

- `--in-enc <name>`   : Charset for reading input files (default: UTF-8)
- `--out-enc <name>`  : Charset for writing output files (default: UTF-8)
- `--con-enc <name>`  : Charset for *console* stdin/stdout on Windows (default: UTF-8)

> The charset `<name>` is any value accepted by Java’s `Charset.forName(...)`.  
> Names are **case-insensitive** and aliases are supported.

### Common charsets (quick list)

- **Unicode**: `UTF-8`, `UTF-16`, `UTF-16LE`, `UTF-16BE`
- **Chinese (Traditional/Simplified)**: `Big5`, `Big5-HKSCS`, `GBK`, `GB18030`, `GB2312`
- **Japanese**: `Shift_JIS`, `windows-31j` (aka MS932), `EUC-JP`, `ISO-2022-JP`
- **Korean**: `EUC-KR`, `MS949` (aka `x-windows-949`)
- **SE Asia**: `TIS-620` (Thai), `windows-1258` (Vietnamese)
- **Cyrillic**: `windows-1251`, `KOI8-R`, `KOI8-U`, `ISO-8859-5`
- **Western Europe**: `ISO-8859-1`, `windows-1252`, `ISO-8859-15`
- **Others (selected)**: `ISO-8859-2/3/4/7/8/9/13/16`, `windows-1250/1253/1254/1255/1256/1257`

> Tip: `GB2312` is commonly an alias handled via `EUC-CN`/`GBK` on modern JDKs. Prefer `GBK` or `GB18030`.

### Examples

```bash
# Linux/macOS (files)
openccjni-cli convert -c t2s -i in_big5.txt --in-enc Big5 -o out_utf8.txt --out-enc UTF-8

# Windows (pipe Big5 into the tool, keep console in GBK)
Get-Content .\in_big5.txt -Encoding Big5 | openccjni-cli.bat convert -c t2s -p --con-enc GBK

# Force UTF-8 console on Windows (PowerShell 7+)
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
openccjni-cli.bat convert -c s2t -p --con-enc UTF-8
```

---

## 📝 License

This project is licensed under the **MIT License**. See [LICENSE](LICENSE).

**Acknowledgements**

- [OpenCC](https://github.com/BYVoid/OpenCC) and related dictionary projects.
- FMMSEG longest-match segmentation.
- `opencc_fmmseg_capi` from [opencc-fmmseg](https://github.com/laisuk/opencc-fmmseg), Rust C API native library that
  powers the JNI bridge.

---

## ❓ FAQ

**Q: Do I need to call `NativeLibLoader`?**  
A: No. `OpenCC` auto-loads the native library (system-first, then resource extract). `NativeLibLoader` remains available
for advanced cases.

**Q: Can I call it from multiple threads?**  
A: Yes. The high-level API maintains per-thread JNI contexts, so parallel conversions are safe and efficient.

**Q: Where do I put the native DLL/SO/DYLIB if not embedding?**  
A: On your system library path (`PATH`, `LD_LIBRARY_PATH`, `DYLD_LIBRARY_PATH`), so `System.loadLibrary` can find it.
