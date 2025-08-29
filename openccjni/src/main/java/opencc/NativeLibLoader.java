package opencc;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native library loader with system-first fallback-to-resources behavior.
 * <p>
 * Resolution order per library base name:
 * <ol>
 *   <li>{@link System#loadLibrary(String)} (PATH / java.library.path / current dir, etc.)</li>
 *   <li>Extract from JAR: {@code /opencc/natives/{os}-{arch}/{System.mapLibraryName(baseName)}} into a shared temp dir, then {@link System#load(String)}</li>
 * </ol>
 * <p>
 * Resource layout examples in your JAR:
 * <ul>
 *   <li>{@code opencc/natives/windows-x86_64/OpenccWrapper.dll}</li>
 *   <li>{@code opencc/natives/windows-x86_64/opencc_fmmseg_capi.dll}</li>
 *   <li>{@code opencc/natives/linux-x86_64/libOpenccWrapper.so}</li>
 *   <li>{@code opencc/natives/macos-aarch64/libOpenccWrapper.dylib}</li>
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
    private static final SecureRandom RNG = new SecureRandom();
    private static volatile Path SHARED_TEMP_DIR; // single extract dir per JVM

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
                throw new UnsatisfiedLinkError("Failed to extract native '" + p.base + "': " + e);
            }
        }

        // Third pass: load extracted ones in order
        for (LoadPlan p : plans) {
            if (p.systemLoaded) continue;
            if (p.extractedPath == null) {
                if (!p.optional) {
                    throw new UnsatisfiedLinkError("Native '" + p.base + "' not found in system or resources.");
                }
                continue; // optional and not present
            }
            final String key = normalizeKey(p.base);
            if (LOADED.putIfAbsent(key, Boolean.TRUE) != null) continue;
            System.load(p.extractedPath.toAbsolutePath().toString());
        }
    }

    /* ===================== Internals ===================== */

    private static boolean trySystemLoad(String baseName) {
        try {
            System.loadLibrary(baseName);
            return true;
        } catch (UnsatisfiedLinkError e) {
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
                    SHARED_TEMP_DIR = Files.createTempDirectory("opencc-natives-");
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
    private static Path extractToDir(String baseName, Path dir, boolean optional) throws IOException {
        final String mapped = System.mapLibraryName(baseName); // keeps 'lib' and extension rules per-OS
        final String os = detectOs();
        final String arch = detectArch();

        final String resource = String.format("/opencc/natives/%s-%s/%s", os, arch, mapped);
        URL url = NativeLibLoader.class.getResource(resource);

        // Raw-filename fallback (rarely needed; keeps behavior from your original version)
        if (url == null && "windows".equals(os)) {
            final String alt = String.format("/opencc/natives/%s-%s/%s.dll", os, arch, baseName);
            url = NativeLibLoader.class.getResource(alt);
            if (url == null && optional) return null;
            if (url == null) {
                throw new FileNotFoundException("Missing resource: " + resource + " (and " + alt + ")");
            }
        } else if (url == null) {
            if (optional) return null;
            throw new FileNotFoundException("Missing resource: " + resource);
        }

        Files.createDirectories(dir);
        final Path out = dir.resolve(mapped);
        try (InputStream in = url.openStream();
             OutputStream o = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) o.write(buf, 0, r);
        }

        // Be generous with perms on Unix
        try {
            out.toFile().setReadable(true, true);
            out.toFile().setWritable(true, true);
            out.toFile().setExecutable(true, true);
        } catch (SecurityException ignored) {
        }

        out.toFile().deleteOnExit();
        return out;
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
        return "linux"; // conservative default
    }

    static String detectArch() {
        String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        arch = arch
                .replace("amd64", "x86_64")
                .replace("x86-64", "x86_64")
                .replace("x64", "x86_64")
                .replace("i386", "x86")
                .replace("i486", "x86")
                .replace("i586", "x86")
                .replace("i686", "x86")
                .replace("arm64", "aarch64");
        if (arch.contains("aarch64")) return "aarch64";
        if (arch.contains("x86_64")) return "x86_64";
        if (arch.equals("arm64")) return "aarch64";
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
