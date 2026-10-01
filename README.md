# OpenccJNI: Java JNI wrapper for opencc-fmmseg

[![Maven Central](https://img.shields.io/maven-central/v/io.github.laisuk/openccjni.svg)](https://central.sonatype.com/artifact/io.github.laisuk/openccjni)
[![javadoc](https://javadoc.io/badge2/io.github.laisuk/openccjni/javadoc.svg)](https://javadoc.io/doc/io.github.laisuk/openccjni)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platforms](https://img.shields.io/badge/platforms-win%20%7C%20linux%20%7C%20macOS-informational)](#-supported-platforms)
[![Total Downloads](https://img.shields.io/github/downloads/laisuk/openccJNI/total.svg)](https://github.com/laisuk/openccJNI/releases)
[![Latest Downloads](https://img.shields.io/github/downloads/laisuk/openccJNI/latest/total.svg)](https://github.com/laisuk/openccJNI/releases/latest)
[![](https://jitpack.io/v/laisuk/OpenccJNI.svg)](https://jitpack.io/#laisuk/OpenccJNI)

> High-performance Simplified/Traditional Chinese conversion using OpenCC with FMMSEG longest-match segmentation — from
> Java, via JNI.

---

## ✨ Features

- **OpenCC + FMMSEG**: Longest-match phrase segmentation backed by OpenCC dictionaries.
- **Fast & parallel-friendly**: Static conversions use thread-local native wrappers and are safe across threads.
- **Auto native load**: `OpenCC` automatically loads the native library (system-first, then from resources) — you
  typically **do not need** to call `NativeLibLoader` yourself.
- **Wide config support**: Common OpenCC configurations out of the box (see below).
- **Small Seal Script conversion**: Simplified/Traditional Chinese ↔ Small Seal Script with the opencc-fmmseg v0.13.0
  native engine.

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
    implementation("io.github.laisuk:openccjni:1.3.0")
}
```

### Maven

```xml

<dependency>
    <groupId>io.github.laisuk</groupId>
    <artifactId>openccjni</artifactId>
    <version>1.3.0</version>
</dependency>
```

> Requires Java 8+

## Quick start

### A) One-liner conversion (static helper)

```java
import openccjni.OpenCC;

public class Demo {
    public static void main(String[] args) {
        String text = "汉字转换测试";
        String out = OpenCC.convert(text, "s2t"); // Simplified → Traditional
        System.out.println(out); // 漢字轉換測試
    }
}
```

### B) Instance usage (persistent `OpenCC` profile)

If you want to reuse one conversion profile across calls:

```java
import openccjni.OpenCC;

public class Demo {
    public static void main(String[] args) {
        try (OpenCC cc = OpenCC.fromConfig("s2t")) {
            String out = cc.convert("汉字转换测试");
            System.out.println(out); // 漢字轉換測試
        }
    }
}
```

Both styles use the public `OpenCC` API. Static helpers retain a thread-local native wrapper. Each configured `OpenCC`
instance owns one native wrapper and should be closed with try-with-resources. Conversion calls may share an instance
concurrently while its configuration remains unchanged; do not call `setConfig(...)` or `close()` during conversion.

### C) Custom dictionary files

Custom dictionaries are UTF-8 text files containing `source<TAB>target` mappings. The following Java 8-compatible
example appends one Hong Kong reverse-phrase mapping:

```java
import openccjni.CustomDictMode;
import openccjni.CustomDictSpec;
import openccjni.DictSlot;
import openccjni.OpenCC;
import openccjni.OpenccConfig;

import java.nio.file.Paths;
import java.util.Collections;

public class CustomDictionaryDemo {
    public static void main(String[] args) {
        CustomDictSpec custom = CustomDictSpec.fromFile(
                DictSlot.HKPhrasesRev,
                Paths.get("my_hk_dict.txt"),
                CustomDictMode.Append);

        try (OpenCC cc = new OpenCC(
                OpenccConfig.HK2SP,
                Collections.singletonList(custom))) {
            System.out.println(cc.convert("這個細路哥很靈活"));
            // 这个小男孩很灵活
        }
    }
}
```

Dictionary files are read once during `OpenCC` construction. `Append` keeps unrelated built-in entries and lets custom
keys win; `Override` clears only the selected slot before inserting custom mappings.

### Supported config names

```
s2t, t2s, s2tw, tw2s, s2twp, tw2sp,
s2hkp, hk2sp, s2hk, hk2s, t2tw, t2twp,
tw2t, tw2tp, t2hk, t2hkp, hk2t, hk2tp,
t2jp, jp2t,
s2seal, t2seal, seal2s, seal2t
```

Example:

```java
import openccjni.OpenCC;

public class ConversionDemo {
    public static void main(String[] args) {
        String converted = OpenCC.convert("後臺處理程序", "t2s");
        System.out.println(converted); // 后台处理程序
    }
}
```

---

## 📦 Library Installation

- **A — JAR with embedded natives (recommended)**  
  Embed natives under:  
  `openccjni/natives/{os}-{arch}/{System.mapLibraryName("OpenccWrapper")}` and,  
  `openccjni/natives/{os}-{arch}/{System.mapLibraryName("opencc_fmmseg_capi")}`  
  `OpenccWrapper` attempts `System.loadLibrary` first, then auto-extracts resources to a shared temp directory and loads
  them with `System.load()`.

- **B — System-installed natives**  
  Put `OpenccWrapper.dll` + `opencc_fmmseg_capi.dll`/ `libOpenccWrapper.so` + `libopencc_fmmseg_capi.so` /
  `libOpenccWrapper.dylib` + `libopencc_fmmseg_capi.dylib` on your `PATH` / `LD_LIBRARY_PATH` /
  `DYLD_LIBRARY_PATH`.

> Published releases are available from Maven Central using the coordinates shown above.

---

## 🚀 Quick Start (auto native load)

```java
import openccjni.OpenCC;

public class Demo {
    public static void main(String[] args) {
        // OpenCC autoloads the native library internally.
        // No need to call NativeLibLoader explicitly.

        // Create an instance bound to a specific config:
        try (OpenCC cc = OpenCC.fromConfig("s2t")) {
            String input = "汉字转换测试";
            String output = cc.convert(input);
            System.out.println(output); // 漢字轉換測試
        }
    }
}
```

> The static `OpenCC.convert(...)` methods use thread-local `OpenccWrapper` instances, so concurrent static
> conversions do not require extra locking in user code.

---

## 🧩 Native loading details

Default resolution order used by `OpenccWrapper`:

1. `System.loadLibrary("OpenccWrapper")` (PATH / `java.library.path` / working dir, etc.)
2. If not found, extract from JAR resources under:
   ```
   /openccjni/natives/{os}-{arch}/{System.mapLibraryName("OpenccWrapper")}
   ```
   Examples:
   ```
   /openccjni/natives/windows-x86_64/OpenccWrapper.dll
   /openccjni/natives/linux-aarch64/libOpenccWrapper.so
   /openccjni/natives/linux-x86_64/libOpenccWrapper.so
   /openccjni/natives/macos-arm64/libOpenccWrapper.dylib
   /openccjni/natives/macos-x86_64/libOpenccWrapper.dylib
   ```

`NativeLibLoader` is still available for advanced/manual loading scenarios, but **not required** for typical usage.

---

## 🔧 Build from source

Build and test the Java projects with the Gradle wrapper:

```bash
./gradlew build
```

Release artifacts already include native libraries for the supported platforms. Most users should not build the native
components manually.

Custom native builds require both the `opencc_fmmseg_capi` library from the matching
[`opencc-fmmseg`](https://github.com/laisuk/opencc-fmmseg) release and the `OpenccWrapper` JNI bridge from this
repository, built against the same ABI. Such builds are intended for maintainers and advanced platform ports; the
project does not currently provide a supported, reproducible native-build procedure for arbitrary toolchains.

---

## 🧵 Threading & performance

- Use the static `OpenCC.convert(...)` methods for thread-safe concurrent conversion; the high-level API keeps
  per-thread JNI handles under the hood for these calls.
- A configured `OpenCC` owns one native wrapper. Its conversion methods may be called concurrently while the config is
  unchanged; do not call `setConfig(...)` or `close()` concurrently with conversion.
- The native library relies on immutable data and minimal synchronization, so parallel conversions are efficient.
- For long-running servers, prefer reusing the instance (pattern B) to amortize initialization.

---

## 🖥️ Supported platforms

| OS      | Arch          | Notes                                  |
|---------|---------------|----------------------------------------|
| Windows | x86_64, x86*  | Win7+ works with compatible toolchains |
| Linux   | x86_64, arm64 | glibc-based                            |
| macOS   | arm64, x86_64 | Modern macOS with Clang toolchain      |

\* 32-bit Windows builds are optional; use `i686` MinGW-w64 and a matching JDK.

---

## 🔜 Roadmap

- Separate **OpenccJiebaJNI** (segmentation/keywords) as its own repo.
- Add platform-classified artifacts to the existing **Maven Central** publication.
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

## 📖 Public API overview

The main public entry point is [`OpenCC`](openccjni/src/main/java/openccjni/OpenCC.java). Strongly typed profiles are
provided by [`OpenccConfig`](openccjni/src/main/java/openccjni/OpenccConfig.java), while
[`OfficeHelper`](openccjni/src/main/java/openccjni/OfficeHelper.java) handles Office, OpenDocument, and EPUB files.
[`NativeLibLoader`](openccjni/src/main/java/openccjni/NativeLibLoader.java) supports advanced native-loading scenarios.
The lower-level [`OpenccWrapper`](openccjni/src/main/java/openccjni/OpenccWrapper.java) is a JNI bridge intended mainly
for advanced use.

### `OpenCC` static API

```text
// Create a new instance with the given config
OpenCC.fromConfig(String config)
OpenCC.fromConfig(OpenccConfig configId)

// One-off conversion
OpenCC.convert(String input, String config)
OpenCC.convert(String input, String config, boolean punctuation)
OpenCC.convert(String input, OpenccConfig configId, boolean punctuation)

// Config helpers
OpenCC.isSupportedConfig(String value)
OpenCC.getSupportedConfigs()

// Chinese text detection
// Returns: 1 = Traditional, 2 = Simplified, 0 = mixed / undetermined
OpenCC.zhoCheck(String text)

// Parallel mode for the current thread's native wrapper
OpenCC.isParallel()
OpenCC.setParallel(boolean isParallel)

// Last error message
OpenCC.getLastError()
OpenCC.setLastError(String lastError)
```

### `OpenCC` instance API

```text
// Constructors
new OpenCC()                         // defaults to "s2t"
new OpenCC(String config)            // invalid input falls back to "s2t"
new OpenCC(OpenccConfig configId)    // null falls back to OpenccConfig.S2T
new OpenCC(List<CustomDictSpec> specs)
new OpenCC(OpenccConfig configId, List<CustomDictSpec> specs)

// Conversion
cc.convert(String input)
cc.convert(String input, boolean punctuation)
cc.normalizeCompat(String input)
cc.normalizeCompatExtended(String input)
cc.deTofu(String input, DeTofuLevel level)

// Config management
cc.getConfig()
cc.getConfigId()
cc.setConfig(String config)
cc.setConfig(OpenccConfig configId)

// Release the instance-owned native wrapper
cc.close()
```

`OpenCC` implements `AutoCloseable`; use try-with-resources for constructed instances.

### Custom dictionary API

```text
CustomDictSpec.fromFile(DictSlot slot, Path path, CustomDictMode mode)
CustomDictSpec.fromFiles(DictSlot slot, List<Path> paths, CustomDictMode mode)

CustomDictMode.Append
CustomDictMode.Override
```

`DictSlot` identifies the exact built-in dictionary to patch, such as `STPhrases`, `HKPhrasesRev`, or
`STPunctuations`. Files use UTF-8 `source<TAB>target` lines; blank lines and `#` or `//` comments are ignored.

### `OpenccConfig` API

```text
OpenccConfig.defaultConfig()
configId.toCanonicalName()
OpenccConfig.tryParse(String value)
OpenccConfig.toCanonicalNameOrNull(String value)
OpenccConfig.supportedCanonicalNames()
OpenccConfig.isValidConfig(String value)
```

### `OfficeHelper` API

```text
OfficeHelper.OFFICE_FORMATS
OfficeHelper.convert(byte[] input, String format, TextConverter converter, boolean keepFont)
OfficeHelper.convert(File input, File output, String format, TextConverter converter, boolean keepFont)
OfficeHelper.convert(byte[] input, String format, OpenCC converter, boolean punctuation, boolean keepFont)
OfficeHelper.convert(File input, File output, String format, OpenCC converter, boolean punctuation, boolean keepFont)
OfficeHelper.zip(Path source, Path destination)

result.success
result.message
memoryResult.data
```

`TextConverter` is a synchronous `String -> String` callback. `OfficeHelper` streams file-based
packages to a sibling temporary ZIP, validates the archive, and publishes it only after success.
The byte-array overload works entirely in memory. Unselected entries are copied through; XLSX
worksheets pass only inline-string text nodes to the callback. Existing `OpenCC` overloads delegate
to the same pipeline and borrow the caller's converter without closing it.

### `NativeLibLoader` API

```text
NativeLibLoader.loadOrExtract(String baseName)
NativeLibLoader.loadChain(String... baseNames)
```

### `OpenccWrapper` advanced API

```text
// Native library metadata
OpenccWrapper.getAbiNumber()
OpenccWrapper.getVersionString()

// Instance lifecycle
new OpenccWrapper()
new OpenccWrapper(List<OpenccWrapper.OpenccCustomDictSpec> specs)
wrapper.close()

// Low-level custom dictionary values
new OpenccWrapper.OpenccCustomPair(String source, String target)
new OpenccWrapper.OpenccCustomDictSpec(int slot, int mode, List<OpenccCustomPair> pairs)

// Low-level conversion
wrapper.convert(String input, String config, boolean punctuation)
wrapper.convertCfg(String input, int configId, boolean punctuation)

// Config id helpers
wrapper.configNameToId(String canonicalName)
wrapper.configNameToId(OpenccConfig configId)
wrapper.configIdToName(int configId)

// Native state helpers
wrapper.isParallel()
wrapper.setParallel(boolean isParallel)
wrapper.zhoCheck(String input)
wrapper.getLastError()
wrapper.clearLastError()
```

### Supported Configurations

The following configuration keys are recognized (matching OpenCC profiles
):

- `s2t` – Simplified → Traditional
- `t2s` – Traditional → Simplified
- `s2tw`, `tw2s`, `s2twp`, `tw2sp` – Taiwan variants
- `s2hk`, `hk2s` – Simplified ↔ Hong Kong variants
- `s2hkp`, `hk2sp` – Simplified ↔ Hong Kong variants, with phrases
- `t2tw`, `tw2t`, `t2twp`, `tw2tp` – Traditional ↔ Taiwan
- `t2hk`, `hk2t` – Traditional ↔ Hong Kong variants
- `t2hkp`, `hk2tp` – Traditional ↔ Hong Kong variants, with phrases
- `t2jp`, `jp2t` – Traditional ↔ Japanese

### Sample Code

```java
import openccjni.OpenCC;
import openccjni.OpenccConfig;

public class ApiDemo {
    public static void main(String[] args) {
        // Static one-off conversion
        String staticOut = OpenCC.convert("汉字", "s2t"); // 漢字

        // Instance API with persistent config
        try (OpenCC cc = OpenCC.fromConfig("tw2s")) {
            String instanceOut = cc.convert("繁體字"); // 繁体字
        }

        // With punctuation conversion
        String punctOut = OpenCC.convert("“汉字”", "s2t", true); // 「漢字」

        // Enum-based config
        String enumOut = OpenCC.convert("汉字", OpenccConfig.S2T, false); // 漢字
    }
}
```

```java
import openccjni.OpenccWrapper;

public class LowLevelDemo {
    public static void main(String[] args) {
        // Low-level wrapper API
        try (OpenccWrapper wrapper = new OpenccWrapper()) {
            String wrapperOut = wrapper.convert("汉字", "s2t", false);
            System.out.println(wrapperOut);
        }
    }
}
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

### Small Seal Script conversion

The opencc-fmmseg v0.13.0 native engine adds four conversion configs:

| Config   | Input → Output                          |
|----------|-----------------------------------------|
| `s2seal` | Simplified Chinese → Small Seal Script  |
| `t2seal` | Traditional Chinese → Small Seal Script |
| `seal2s` | Small Seal Script → Simplified Chinese  |
| `seal2t` | Small Seal Script → Traditional Chinese |

For PowerShell pipelines, use UTF-8 so supplementary seal characters reach the CLI intact:

```powershell
$OutputEncoding = [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
echo "你𿒛，𽌠𽴖「𾇓𿭖𿛛碼18」" | .\bin\openccjni-cli.bat convert -c seal2t
```

```text
Output (Charset: UTF-8)
你好，小篆「國際編碼18」
```

Using `-c seal2s` with the same input produces `你好，小篆「国际编码18」`.
To convert ordinary Chinese into seal characters:

```powershell
echo "你好，小篆「國際編碼18」" | .\bin\openccjni-cli.bat convert -c t2seal
echo "你好，小篆「国际编码18」" | .\bin\openccjni-cli.bat convert -c s2seal
```

Both examples produce:

```text
Output (Charset: UTF-8)
你𿒛，𽌠𽴖「𾇓𿭖𿛛碼18」
```

For file input/output, save the input files as UTF-8. For example, `seal.txt` contains
`你𿒛，𽌠𽴖「𾇓𿭖𿛛碼18」`:

```powershell
.\bin\openccjni-cli.bat convert -c seal2t -i seal.txt -o traditional.txt --in-enc UTF-8 --out-enc UTF-8
.\bin\openccjni-cli.bat convert -c seal2s -i seal.txt -o simplified.txt --in-enc UTF-8 --out-enc UTF-8
.\bin\openccjni-cli.bat convert -c t2seal -i traditional.txt -o seal-from-traditional.txt --in-enc UTF-8 --out-enc UTF-8
.\bin\openccjni-cli.bat convert -c s2seal -i simplified.txt -o seal-from-simplified.txt --in-enc UTF-8 --out-enc UTF-8
```

`traditional.txt` contains `你好，小篆「國際編碼18」`, and `simplified.txt` contains
`你好，小篆「国际编码18」`. Both seal output files contain the original sample.
UTF-8 is already the default for file input/output; the encoding flags above make it explicit.

The Java API accepts the same names and their typed equivalents:
`OpenccConfig.S2SEAL`, `OpenccConfig.T2SEAL`, `OpenccConfig.SEAL2S`, and `OpenccConfig.SEAL2T`.

### Normalization and DeTofu

The `convert`, `office`, and PDF conversion commands share these options:

| Option                         | Behavior                                                                                                                         |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `-n`, `--norm-compat`          | Normalize CJK Compatibility Ideographs before conversion.                                                                        |
| `-E`, `--norm-compat-extended` | Normalize extended compatibility/allograph forms and CJK Compatibility Ideographs before conversion. Takes precedence over `-n`. |
| `--detofu=<level>`             | Apply built-in fallback mappings after conversion. Levels: `all`, `ext-b` through `ext-i`.                                       |

DeTofu thresholds are inclusive: `all` is `ext-b`, and `ext-c` covers ExtC through ExtI.
The stable aliases `extb`/`b` through `exti`/`i` are also accepted, ignoring case.
Unmapped characters remain unchanged. PDF extract-only mode reports these options as ignored.

```powershell
.\bin\openccjni-cli.bat convert -c t2s -i input.txt -o output.txt -E --detofu all
.\bin\openccjni-cli.bat office -c t2s -i book.docx -E --detofu ext-b --keep-font
```

Normalization, Chinese conversion, punctuation, and DeTofu all use the same instance-owned
JNI wrapper. Close the `OpenCC` owner only after its `TextConverter` callback has finished.

This CLI follows the stable OpenccJava v1.5.0 text/Office/PDF structure, option names, processing
order, and encoding behavior. OpenccJNI retains its native loader, platform resources, launchers,
and custom-dictionary parser. It does not provide OpenccJava's `dictgen` tool or `--detofu-file`:
the native wrapper has no corresponding dictionary-generation/custom-DeTofu API. No pure-Java
conversion engine or fallback mapping tables are bundled. Dictionary and mapping results remain
those of the bundled native engine.

Commands return failure codes to Picocli; only the entry point exits the process, allowing native
owners to close on both success and failure. `convert` rejects invalid configs as usage errors;
Office retains the stable reference's library-default fallback for unknown configs.

Encoding matches the reference: file I/O uses `--in-enc`/`--out-enc`; redirected stdin uses
`--in-enc`; interactive stdin uses `--con-enc`. Stdout uses `--con-enc` even when redirected (`--out-enc` applies to
output files). Office package XML is processed as UTF-8; PDF output is UTF-8.

The updated Norm/extended Norm/DeTofu paths have been tested on Windows x86_64 only.
Updated macOS/Linux JNI bridges must be supplied before those platforms can use these new paths.

### Plain Text Conversion:

```
bin/openccjni-cli convert --help
Usage: openccjni-cli convert [-EhnpV] -c=<conversion> [--con-enc=<encoding>]
                             [--detofu=<level>] [-i=<file>]
                             [--in-enc=<encoding>] [-o=<file>]
                             [--out-enc=<encoding>] [-D=<slot:mode:path>[,<slot:
                             mode:path>...]]...
Convert plain text using OpenccJNI
  -c, --config=<conversion>  Conversion configuration. Supported: s2t, t2s,
                               s2tw, tw2s, s2twp, tw2sp, s2hkp, hk2sp, s2hk,
                               hk2s, t2tw, t2twp, tw2t, tw2tp, t2hk, t2hkp,
                               hk2t, hk2tp, t2jp, jp2t, s2seal, t2seal, seal2s,
                               seal2t
      --con-enc=<encoding>   Console encoding for interactive mode. Ignored if
                               not attached to a terminal. Common <encoding>:
                               UTF-8, GBK, Big5
  -D, --custom-dict=<slot:mode:path>[,<slot:mode:path>...]
                             Apply custom dictionary file. Format: slot:
                               append|override:path. Can be repeated or
                               comma-separated. Supported slots: STCharacters,
                               STPhrases, STPunctuations, TSCharacters,
                               TSPhrases, TSPunctuations, TWPhrases,
                               TWPhrasesRev, TWVariants, TWVariantsPhrases,
                               TWVariantsRev, TWVariantsRevPhrases, HKPhrases,
                               HKPhrasesRev, HKVariants, HKVariantsPhrases,
                               HKVariantsRev, HKVariantsRevPhrases,
                               JPSCharacters, JPSCharactersRev, JPSPhrases
      --detofu=<level>       Apply tofu-safe fallback after conversion: all,
                               ext-b, ext-c, ext-d, ext-e, ext-f, ext-g, ext-h,
                               ext-i
  -E, --norm-compat-extended Normalize extended Unicode compatibility/allograph
                               forms and CJK Compatibility Ideographs before
                               conversion.
  -h, --help                 Show this help message and exit.
  -i, --input=<file>         Input file
      --in-enc=<encoding>    Input encoding
  -n, --norm-compat          Normalize CJK Compatibility Ideographs before
                               conversion.
  -o, --output=<file>        Output file
      --out-enc=<encoding>   Output encoding
  -p, --punct                Punctuation conversion (default: false)
  -V, --version              Print version information and exit.
```

Custom dictionaries can be repeated with `-D`/`--custom-dict`. Slot names are case-insensitive and may include or omit
hyphens and underscores. The `append` mode preserves unrelated entries; `override` clears the selected slot first.

PowerShell example using [`openccjni-cli/src/test/data/my_hk_dict.txt`](openccjni-cli/src/test/data/my_hk_dict.txt):

```powershell
echo "這個細路哥很靈活" |
    .\bin\openccjni-cli.bat convert -c hk2sp `
    -D HkPhrasesRev:append:openccjni-cli/src/test/data/my_hk_dict.txt
```

```text
Output (Charset: UTF-8)
这个小男孩很灵活
```

### Office Document Conversion:

Supported Office document formats: `.docx`, `.xlsx`, `.pptx`, `.odt`, `.ods`, `.odp`, `.epub`

```bash
bin/openccjni-cli.bat office -c s2t -i book.docx -o book_converted.docx
```

```
bin/openccjni-cli office --help
Usage: openccjni-cli office [-EFhknpV] -c=<conversion> [--detofu=<level>]
                            [-f=<format>] -i=<file> [-o=<file>] [-D=<slot:mode:
                            path>[,<slot:mode:path>...]]...
Convert Office documents using OpenccJNI
  -c, --config=<conversion>
                           Conversion configuration. Supported: s2t, t2s, s2tw,
                             tw2s, s2twp, tw2sp, s2hkp, hk2sp, s2hk, hk2s,
                             t2tw, t2twp, tw2t, tw2tp, t2hk, t2hkp, hk2t,
                             hk2tp, t2jp, jp2t, s2seal, t2seal, seal2s, seal2t
  -D, --custom-dict=<slot:mode:path>[,<slot:mode:path>...]
                           Apply custom dictionary file. Format: slot:
                             append|override:path. Can be repeated or
                             comma-separated. Supported slots: STCharacters,
                             STPhrases, STPunctuations, TSCharacters,
                             TSPhrases, TSPunctuations, TWPhrases,
                             TWPhrasesRev, TWVariants, TWVariantsPhrases,
                             TWVariantsRev, TWVariantsRevPhrases, HKPhrases,
                             HKPhrasesRev, HKVariants, HKVariantsPhrases,
                             HKVariantsRev, HKVariantsRevPhrases,
                             JPSCharacters, JPSCharactersRev, JPSPhrases
      --detofu=<level>     Apply tofu-safe fallback after conversion: all,
                             ext-b, ext-c, ext-d, ext-e, ext-f, ext-g, ext-h,
                             ext-i
  -E, --norm-compat-extended
                           Normalize extended Unicode compatibility/allograph
                             forms and CJK Compatibility Ideographs before
                             conversion.
  -f, --format=<format>    Target Office format (e.g., docx, xlsx, pptx, odt,
                             epub)
  -F, --convert-filename   Convert the output filename using the selected
                             OpenCC configuration.
  -h, --help               Show this help message and exit.
  -i, --input=<file>       Input Office file
  -k, --[no-]keep-font     Preserve font-family info (default: false)
  -n, --norm-compat        Normalize CJK Compatibility Ideographs before
                             conversion.
  -o, --output=<file>      Output Office file
  -p, --punct              Punctuation conversion (default: false)
  -V, --version            Print version information and exit.
```

#### Optional flags:

- `--punct`: Enable punctuation conversion.
- `--keep-font` / `--no-keep-font`: Preserve original fonts (Office only).
- `--in-enc` / `--out-enc`: Specify encoding for plain-text conversion (e.g. `GBK`, `BIG5`, `UTF-8`).
- `--format`: Force document format (e.g., `docx`, `epub`).

---

### PDF Document Conversion:

Supported **Text-Embedded PDF** document only.

```bash
bin/openccjni-cli.bat pdf -c s2t -p -i book.pdf -o book_converted.txt --reflow
```

```
bin/openccjni-cli pdf --help
Usage: openccjni-cli pdf [-CeEhHnprV] [-c=<conversion>] [--detofu=<level>]
                         -i=<file> [-o=<file>] [-D=<slot:mode:path>[,<slot:mode:
                         path>...]]...
Extract PDF text, optionally reflow CJK paragraphs, then convert with OpenccJNI
  -c, --config=<conversion>
                         Conversion configuration. Supported: s2t, t2s, s2tw,
                           tw2s, s2twp, tw2sp, s2hkp, hk2sp, s2hk, hk2s, t2tw,
                           t2twp, tw2t, tw2tp, t2hk, t2hkp, hk2t, hk2tp, t2jp,
                           jp2t, s2seal, t2seal, seal2s, seal2t
  -C, --compact          Compact / tighten paragraph gaps after reflow
                           (default: false)
  -D, --custom-dict=<slot:mode:path>[,<slot:mode:path>...]
                         Apply custom dictionary file. Format: slot:
                           append|override:path. Can be repeated or
                           comma-separated. Supported slots: STCharacters,
                           STPhrases, STPunctuations, TSCharacters, TSPhrases,
                           TSPunctuations, TWPhrases, TWPhrasesRev, TWVariants,
                           TWVariantsPhrases, TWVariantsRev,
                           TWVariantsRevPhrases, HKPhrases, HKPhrasesRev,
                           HKVariants, HKVariantsPhrases, HKVariantsRev,
                           HKVariantsRevPhrases, JPSCharacters,
                           JPSCharactersRev, JPSPhrases
      --detofu=<level>   Apply tofu-safe fallback after conversion: all, ext-b,
                           ext-c, ext-d, ext-e, ext-f, ext-g, ext-h, ext-i
  -e, --extract          Extract text from PDF document only (default: false)
  -E, --norm-compat-extended
                         Normalize extended Unicode compatibility/allograph
                           forms and CJK Compatibility Ideographs before
                           conversion.
  -h, --help             Show this help message and exit.
  -H, --header           Insert per-page header markers into extracted text
  -i, --input=<file>     Input PDF file
  -n, --norm-compat      Normalize CJK Compatibility Ideographs before
                           conversion.
  -o, --output=<file>    Output text file (UTF-8). If omitted,
                           '<name>_converted.txt' is used next to input.
  -p, --punct            Enable punctuation conversion (default: false)
  -r, --reflow           Reflow CJK paragraphs after extraction (default: false)
  -V, --version          Print version information and exit.
```

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
A: The static `OpenCC.convert(...)` methods are safe for concurrent use because they maintain per-thread JNI contexts.
Configured `OpenCC` instances are mutable; confine each instance to one thread or synchronize access externally.

**Q: Where do I put the native DLL/SO/DYLIB if not embedding?**  
A: On your system library path (`PATH`, `LD_LIBRARY_PATH`, `DYLD_LIBRARY_PATH`), so `System.loadLibrary` can find it.
