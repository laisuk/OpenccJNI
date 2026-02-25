package openccjni;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
// import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native library loader with system-first fallback-to-resources behavior.
 * <p>
 * Resolution order per library base name:
 * <ol>
 *   <li>{@link System#loadLibrary(String)} (PATH / java.library.path / current dir, etc.)</li>
 *   <li>Extract from JAR: {@code /openccjni/natives/{os}-{arch}/{System.mapLibraryName(baseName)}} into a shared temp dir, then {@link System#load(String)}</li>
 * </ol>
 * <p>
 * Resource layout examples in your JAR:
 * <ul>
 *   <li>{@code openccjni/natives/windows-x86_64/OpenccWrapper.dll}</li>
 *   <li>{@code openccjni/natives/windows-x86_64/opencc_fmmseg_capi.dll}</li>
 *   <li>{@code openccjni/natives/linux-x86_64/libOpenccWrapper.so}</li>
 *   <li>{@code openccjni/natives/macos-arm64/libOpenccWrapper.dylib}</li>
 * </ul>
 * <p>
 * Extras:
 * <ul>
 *   <li>Optional entries: prefix with '?' to ignore missing resource (e.g., {@code "?libwinpthread-1"}).</li>
 *   <li>Idempotent: same baseName won’t be loaded twice.</li>
 * </ul>
 */
public final class NativeLibLoader {
    private static final ConcurrentHashMap<String, Boolean> LOADED = new ConcurrentHashMap<>();
    // private static final SecureRandom RNG = new SecureRandom();
    private static volatile Path SHARED_TEMP_DIR; // single extract dir per JVM
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private NativeLibLoader() {
    }

    /* ===================== Public API ===================== */

    /**
     * Load a single library by base name: try system first, else extract+load.
     *
     * @param baseName the logical library name (unmapped, e.g. "OpenccWrapper");
     *                 mapped via {@link System#mapLibraryName(String)} for the actual file
     * @throws UnsatisfiedLinkError if the library cannot be found or loaded
     */
    @SuppressWarnings("unused")
    public static void loadOrExtract(String baseName) {
        final String key = normalizeKey(baseName);
        if (LOADED.putIfAbsent(key, Boolean.TRUE) != null) return; // already loaded

        // 1) System-first
        if (trySystemLoad(baseName)) return;

        // 2) Resource fallback
        final Path dir = ensureSharedTempDir();
        final Path extracted = extractToDirOrThrow(baseName, dir);
        System.load(extracted.toAbsolutePath().toString());
    }

    /**
     * Load a chain of libraries (dependencies first, main last).
     * <p>
     * Each entry is attempted with system-first, then resource fallback.
     * Optional entries can be prefixed with '?' — missing resources are skipped without error.
     *
     * @param baseNames one or more library base names to load, in dependency order;
     *                  prefix with '?' to mark as optional
     * @throws UnsatisfiedLinkError if any required library cannot be found or loaded
     */
    public static void loadChain(String... baseNames) {
        if (baseNames == null || baseNames.length == 0) return;
        final Path dir = ensureSharedTempDir();

        // First pass: try system load for all
        final List<LoadPlan> plans = new ArrayList<>(baseNames.length);
        for (String raw : baseNames) {
            boolean optional = raw != null && raw.startsWith("?");
            String base = optional ? raw.substring(1) : raw;
            if (base == null || base.isEmpty()) continue;

            final String key = normalizeKey(base);
            if (LOADED.containsKey(key)) {
                plans.add(new LoadPlan(base, true, optional, null)); // already loaded
                continue;
            }
            if (trySystemLoad(base)) {
                LOADED.putIfAbsent(key, Boolean.TRUE);
                plans.add(new LoadPlan(base, true, optional, null));
            } else {
                plans.add(new LoadPlan(base, false, optional, null)); // will extract
            }
        }

        // Second pass: extract any that didn’t load from system
        for (int i = 0; i < plans.size(); i++) {
            LoadPlan p = plans.get(i);
            if (p.systemLoaded || LOADED.containsKey(normalizeKey(p.base))) continue;

            try {
                Path extracted = extractToDir(p.base, dir, p.optional);
                plans.set(i, new LoadPlan(p.base, false, p.optional, extracted));
            } catch (IOException e) {
                if (p.optional) {
                    // optional: skip if missing/unavailable
                    continue;
                }
                System.err.println("Failed to extract native '" + p.base + "': " + e);
                throw new UnsatisfiedLinkError("Failed to extract native '" + p.base + "': " + e);
            }
        }

        // Third pass: load extracted ones in order (with per-lib locks + rollback)
        for (LoadPlan p : plans) {
            if (p.systemLoaded) continue;
            if (p.extractedPath == null) {
                if (!p.optional) {
                    throw new UnsatisfiedLinkError("Native '" + p.base + "' not found in system or resources.");
                }
                continue; // optional and not present
            }
            final String key = normalizeKey(p.base);
            final Object lock = LOCKS.computeIfAbsent(key, k -> new Object());
            synchronized (lock) {
                if (LOADED.containsKey(key)) continue; // another thread raced and won
                LOADED.putIfAbsent(key, Boolean.TRUE);
                try {
                    System.load(p.extractedPath.toAbsolutePath().toString());
                } catch (Throwable t) {
                    LOADED.remove(key); // rollback the optimistic mark
//                    System.err.println("Failed to load native '" + p.base + "' from " + p.extractedPath + ": " + t);
                    throw t;
                }
            }
        }
    }

    /* ===================== Internals ===================== */

    private static boolean trySystemLoad(String baseName) {
        try {
            System.loadLibrary(baseName);
            return true;
        } catch (UnsatisfiedLinkError e) {
//            System.err.println("System.loadLibrary(\"" + baseName + "\") failed: " + e);
            return false;
        }
    }

    private static String normalizeKey(String baseName) {
        return baseName == null ? "" : baseName.toLowerCase(Locale.ROOT);
    }

    private static Path ensureSharedTempDir() {
        Path dir = SHARED_TEMP_DIR;
        if (dir != null) return dir;
        synchronized (NativeLibLoader.class) {
            if (SHARED_TEMP_DIR == null) {
                try {
                    SHARED_TEMP_DIR = Files.createTempDirectory("openccjni-natives-");
                    SHARED_TEMP_DIR.toFile().deleteOnExit();
                } catch (IOException e) {
                    throw new UnsatisfiedLinkError("Failed to create native temp dir: " + e);
                }
            }
            return SHARED_TEMP_DIR;
        }
    }

    private static Path extractToDirOrThrow(String baseName, Path dir) {
        try {
            return extractToDir(baseName, dir, /*optional*/ false);
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native '" + baseName + "': " + e);
        }
    }

    /**
     * Extract a resource-mapped library to the given directory.
     * Returns null if optional and resource is missing.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static Path extractToDir(String baseName, Path dir, boolean optional) throws IOException {
        final String mapped = System.mapLibraryName(baseName); // e.g. libOpenccWrapper.so / .dylib / OpenccWrapper.dll
        final String os = detectOs();
        final String arch = detectArch();

        // Build candidate resource paths (handles macOS arm64/aarch64 preferences internally)
        final List<String> candidates = new ArrayList<>(getNativeStrings(os, arch, mapped));

        // If (and only if) your Windows resources use a *non-mapped* file name, add that variant.
        if ("windows".equals(os)) {
            String rawDll = baseName + ".dll";
            if (!rawDll.equals(mapped)) {
                candidates.add(String.format("/openccjni/natives/%s-%s/%s", os, arch, rawDll));
            }
        }

        // Try each candidate exactly once
        URL url = null;
        for (String res : candidates) {
            url = NativeLibLoader.class.getResource(res);
            if (url != null) break;
        }

        if (url == null) {
            if (optional) return null;
            throw new FileNotFoundException("Missing native resource for " + baseName + " (looked in: " + candidates + ")");
        }

        Files.createDirectories(dir);
        final Path out = dir.resolve(mapped);

        try (InputStream in = url.openStream()) {
            Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // Make executable on Unix-y systems; ignore if not supported
        try {
            // Prefer POSIX if available
            Set<java.nio.file.attribute.PosixFilePermission> perms =
                    EnumSet.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                            java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
                    );
            Files.setPosixFilePermissions(out, perms);
        } catch (UnsupportedOperationException | SecurityException ignore) {
            // Fallback for non-POSIX (e.g., Windows) or restricted FS
            try {
                File f = out.toFile();
                f.setReadable(true, false);
                f.setWritable(true, false);
                f.setExecutable(true, false);
            } catch (SecurityException ignore2) { /* best-effort */ }
        }

        out.toFile().deleteOnExit();
        return out;
    }

    private static List<String> getNativeStrings(String os, String arch, String mapped) {
        final List<String> candidates = new ArrayList<>(4);

        // macOS: keep existing dual naming
        if ("macos".equals(os)) {
            if ("aarch64".equals(arch) || "arm64".equals(arch)) {
                candidates.add(String.format("/openccjni/natives/macos-arm64/%s", mapped));
                candidates.add(String.format("/openccjni/natives/macos-aarch64/%s", mapped));
                return candidates;
            }
            candidates.add(String.format("/openccjni/natives/%s-%s/%s", os, arch, mapped));
            return candidates;
        }

        // Linux: allow both linux-aarch64 and linux-arm64 layouts (people use both)
        if ("linux".equals(os) && "aarch64".equals(arch)) {
            candidates.add(String.format("/openccjni/natives/linux-aarch64/%s", mapped));
            candidates.add(String.format("/openccjni/natives/linux-arm64/%s", mapped));
            return candidates;
        }

        // default: {os}-{arch}
        candidates.add(String.format("/openccjni/natives/%s-%s/%s", os, arch, mapped));
        return candidates;
    }

    /* ===================== OS/Arch helpers ===================== */

    static boolean isWindows() {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    static boolean isMac() {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    static boolean isLinux() {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("nux") || os.contains("nix");
    }

    static String detectOs() {
        if (isWindows()) return "windows";
        if (isMac()) return "macos";
        if (isLinux()) return "linux";
        return "unknown"; // unsupported OS
    }

    static String detectArch() {
        String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);

        // Normalize common aliases
        arch = arch
                .replace("amd64", "x86_64")
                .replace("x86-64", "x86_64")
                .replace("x64", "x86_64")
                .replace("i386", "x86")
                .replace("i486", "x86")
                .replace("i586", "x86")
                .replace("i686", "x86")
                // JVM often reports "aarch64", sometimes "arm64"
                .replace("arm64", "aarch64");

        if (arch.contains("aarch64")) return "aarch64";
        if (arch.contains("x86_64")) return "x86_64";
        if (arch.matches(".*\\bx86\\b.*")) return "x86";
        return arch; // pass through unknowns (e.g., riscv64)
    }

    /* ===================== small struct ===================== */

    private static final class LoadPlan {
        final String base;
        final boolean systemLoaded;
        final boolean optional;
        final Path extractedPath;

        LoadPlan(String base, boolean systemLoaded, boolean optional, Path extractedPath) {
            this.base = base;
            this.systemLoaded = systemLoaded;
            this.optional = optional;
            this.extractedPath = extractedPath;
        }
    }
}
