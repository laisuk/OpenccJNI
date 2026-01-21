package openccjni;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Java binding for <a href="https://github.com/BYVoid/OpenCC">OpenCC</a> (Open Chinese Convert)
 * via the {@code opencc-fmmseg-capi} native library.
 *
 * <p>Provides both static, one-off conversion helpers and an instance API with a persistent
 * conversion profile. The class attempts to load the JNI wrapper {@code OpenccWrapper} from
 * the system first (e.g., {@code PATH}, {@code java.library.path}), and if not found,
 * it falls back to resources embedded in the JAR under:
 *
 * <pre>
 * /openccjni/natives/&lt;os&gt;-&lt;arch&gt;/
 *   linux-x86_64/    libOpenccWrapper.so, libopencc_fmmseg_capi.so
 *   macos-arm64/     libOpenccWrapper.dylib, libopencc_fmmseg_capi.dylib
 *   windows-x86_64/  OpenccWrapper.dll,    opencc_fmmseg_capi.dll
 * </pre>
 *
 * <p><b>Thread-safety:</b> A {@link ThreadLocal} wrapper instance is used internally, so calls
 * to the static helpers are safe to use concurrently across threads.
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * // Static one-off conversion
 * String s = OpenCC.convert("汉字", "s2t");
 *
 * // Instance-based conversion with persistent profile
 * OpenCC cc = OpenCC.fromConfig("tw2s");
 * String out = cc.convert("繁體字");
 * }</pre>
 *
 * @since 1.0.0
 */
public final class OpenCC {
    static {
        try {
            // 1) Try normal search path (PATH, java.library.path, working dir, etc.)
            System.loadLibrary("OpenccWrapper");
        } catch (UnsatisfiedLinkError e) {
            // 2) Fallback to resources packaged in the JAR
            NativeLibLoader.loadChain(
                    "opencc_fmmseg_capi",
                    "OpenccWrapper"
            );
        }
    }

    /**
     * Thread-local native wrapper for OpenCC (safe for parallel use).
     */
    private static final ThreadLocal<OpenccWrapper> WRAPPER =
            ThreadLocal.withInitial(OpenccWrapper::new);

    /**
     * Default configuration if none is specified.
     */
    private OpenccConfig configId;

    /**
     * Cached native config id (opencc_config_t).
     * Lazily resolved on first use; reset to -1 when config changes.
     */
    private volatile int resolvedNumericId = -1;

    /**
     * Cache enum -> native numeric id (opencc_config_t). Values are stable across runs.
     *
     * <p>Even though resolving is cheap, caching avoids repeated JNI calls in hot loops.</p>
     */
    private static final ConcurrentMap<OpenccConfig, Integer> CONFIG_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * Last error message encountered by OpenCC operations (Java-side).
     *
     * <p>Priority: Java-side error first; if empty, fall back to native last error.</p>
     */
    private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();

    // ---------- Constructors / factories ----------

    /**
     * Creates an {@code OpenCC} instance with the default configuration ({@code "s2t"}).
     *
     * @since 1.0.0
     */
    public OpenCC() {
        this.configId = OpenccConfig.defaultConfig();
    }

    /**
     * Creates an {@code OpenCC} instance with the specified configuration string.
     *
     * <p>The provided configuration string is parsed in a case-insensitive manner.
     * Both canonical OpenCC names (for example {@code "s2t"}, {@code "t2twp"})
     * and enum-style names (for example {@code "S2T"}, {@code "T2TWP"}) are accepted.</p>
     *
     * <p>If the configuration string is {@code null}, empty, or invalid, the default configuration
     * ({@code "s2t"}) is used and an error is recorded via {@link #getLastError()}.</p>
     *
     * @param config configuration key; may be {@code null}
     * @since 1.0.0
     */
    public OpenCC(String config) {
        OpenccConfig parsed = OpenccConfig.tryParse(config);
        if (parsed == null) {
            setLastError("Invalid config: " + config);
            parsed = OpenccConfig.defaultConfig();
        }
        this.configId = parsed;
    }

    /**
     * Creates an {@code OpenCC} instance with the specified configuration identifier.
     *
     * <p>If {@code configId} is {@code null}, the default configuration ({@code "s2t"}) is used
     * and an error is recorded.</p>
     *
     * @param configId OpenCC config id; may be {@code null}
     * @since 1.0.0
     */
    public OpenCC(OpenccConfig configId) {
        if (configId == null) {
            setLastError("Config is null");
            configId = OpenccConfig.defaultConfig();
        }
        this.configId = configId;
    }

    /**
     * Creates a new {@link OpenCC} instance with the given configuration string.
     * If invalid, {@code "s2t"} is used instead.
     *
     * @param config configuration key
     * @return instance
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public static OpenCC fromConfig(String config) {
        return new OpenCC(config);
    }

    /**
     * Creates a new {@link OpenCC} instance with the given configuration id.
     *
     * @param configId config id; may be {@code null}
     * @return instance
     * @since 1.0.0
     */
    public static OpenCC fromConfig(OpenccConfig configId) {
        return new OpenCC(configId);
    }

    // ---------- Static helpers ----------

    /**
     * Converts the given text using the specified configuration.
     *
     * @param input  input text (non-null; empty allowed)
     * @param config configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @return converted text; if config invalid, returns input unchanged and records error
     * @since 1.0.0
     */
    public static String convert(String input, String config) {
        return convert(input, config, false);
    }

    /**
     * Converts the given text using the specified configuration, optionally converting punctuation.
     *
     * <p>If {@code config} is invalid, returns {@code input} unchanged and records
     * the error via {@link #getLastError()}.</p>
     *
     * @param input       input text (non-null; empty allowed)
     * @param config      configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @param punctuation whether to convert punctuation
     * @return converted text; if config invalid, returns input unchanged and records error
     * @since 1.0.0
     */
    public static String convert(String input, String config, boolean punctuation) {
        if (input == null) {
            setLastError("Input is null");
            return null;
        }
        if (input.isEmpty()) {
            setLastError(null);
            return "";
        }

        OpenccConfig cfg = OpenccConfig.tryParse(config);
        if (cfg == null) {
            setLastError("Invalid config: " + config);
            return input;
        }

        return convert(input, cfg, punctuation);
    }

    /**
     * Converts the given text using the specified OpenCC configuration.
     *
     * <p>Fast path: resolves enum -&gt; numeric id once (cached) and calls native {@code convertCfg}.</p>
     *
     * @param input       input text; may be {@code null}
     * @param configId    OpenCC config id; may be {@code null}
     * @param punctuation whether to convert punctuation
     * @return converted text; {@code null} if input null; input unchanged if configId null
     * @since 1.0.0
     */
    public static String convert(String input, OpenccConfig configId, boolean punctuation) {
        if (input == null) {
            setLastError("Input is null");
            return null;
        }
        if (input.isEmpty()) {
            setLastError(null);
            return "";
        }
        if (configId == null) {
            setLastError("Config is null");
            return input;
        }

        final OpenccWrapper w = WRAPPER.get();
        final int cfgId = CONFIG_ID_CACHE.computeIfAbsent(configId, w::configNameToId);
        if (cfgId < 0) {
            // Should not happen for a real enum value, but keep the tolerant contract.
            setLastError("Invalid config: " + configId.toCanonicalName());
            return input;
        }

        setLastError(null);
        return w.convertCfg(input, cfgId, punctuation);
    }

    /**
     * Checks whether the provided text contains Chinese characters.
     *
     * @param text input text (maybe null/empty)
     * @return integer flag from native layer; 0 if none detected
     * @since 1.0.0
     */
    public static int zhoCheck(String text) {
        if (text == null || text.isEmpty()) {
            setLastError(null);
            return 0;
        }
        setLastError(null);
        return WRAPPER.get().zhoCheck(text);
    }

    /**
     * Checks whether global parallel conversion mode is enabled.
     *
     * @return true if enabled
     * @since 1.0.2
     */
    public static boolean isParallel() {
        return WRAPPER.get().isParallel();
    }

    /**
     * Enables/disables the global parallel conversion mode.
     *
     * @param isParallel true to enable
     * @since 1.0.2
     */
    public static void setParallel(boolean isParallel) {
        WRAPPER.get().setParallel(isParallel);
    }

    /**
     * Returns an immutable list of supported configuration keys.
     *
     * @return supported canonical config names
     * @since 1.0.0
     */
    public static List<String> getSupportedConfigs() {
        return OpenccConfig.supportedCanonicalNames();
    }

    // ---------- Instance API ----------

    /**
     * Converts the input text using this instance's configuration.
     *
     * @param input input text (non-null; empty allowed)
     * @return converted text
     * @since 1.0.0
     */
    public String convert(String input) {
        return convert(input, false);
    }

    /**
     * Converts the input text using this instance's configuration.
     *
     * <p>Fast path: config enum resolved to native numeric id once and cached.</p>
     *
     * @param input       input text (non-null; empty allowed)
     * @param punctuation whether to convert punctuation
     * @return converted text; null if input null
     * @since 1.0.0
     */
    public String convert(String input, boolean punctuation) {
        if (input == null) {
            setLastError("Input is null");
            return null;
        }
        if (input.isEmpty()) {
            setLastError(null);
            return "";
        }

        setLastError(null);

        final OpenccWrapper w = WRAPPER.get();
        final int cfgId = resolveConfigNumericId(w);
        return w.convertCfg(input, cfgId, punctuation);
    }

    /**
     * Returns the canonical configuration name used by this instance.
     *
     * @return canonical config name (lowercase), never null
     * @since 1.0.0
     */
    public String getConfig() {
        return this.configId.toCanonicalName();
    }

    /**
     * Returns the strongly-typed configuration id used by this instance.
     *
     * @return config id, never null
     * @since 1.0.0
     */
    public OpenccConfig getConfigId() {
        return this.configId;
    }

    /**
     * Checks whether a configuration string corresponds to a supported
     * OpenCC conversion configuration.
     *
     * <p>The check is case-insensitive and accepts both canonical names
     * (for example {@code "s2t"}, {@code "t2twp"}) and enum-style names
     * (for example {@code "S2T"}, {@code "T2TWP"}).</p>
     *
     * <p>This method performs no allocation beyond parsing and never throws.</p>
     *
     * @param value the configuration string to check; may be {@code null}
     * @return {@code true} if the configuration is supported; {@code false} otherwise
     */
    public static boolean isSupportedConfig(String value) {
        return OpenccConfig.isValidConfig(value);
    }

    /**
     * Updates this instance's configuration using a configuration string.
     *
     * <p>If invalid, defaults to {@code "s2t"} and records an error.</p>
     *
     * @param config configuration key; may be null
     * @since 1.0.0
     */
    public void setConfig(String config) {
        OpenccConfig parsed = OpenccConfig.tryParse(config);
        if (parsed == null) {
            setLastError("Invalid config: " + config);
            parsed = OpenccConfig.defaultConfig();
        }
        this.configId = parsed;
        this.resolvedNumericId = -1;
    }

    /**
     * Updates this instance's configuration using a strongly-typed id.
     *
     * <p>If null, defaults to {@code "s2t"} and records an error.</p>
     *
     * @param configId config id; may be null
     * @since 1.0.0
     */
    public void setConfig(OpenccConfig configId) {
        if (configId == null) {
            setLastError("Config is null");
            configId = OpenccConfig.defaultConfig();
        }
        this.configId = configId;
        this.resolvedNumericId = -1;
    }

    // ---------- Error handling ----------

    /**
     * Returns the last error message (Java-side error has priority; otherwise the native error).
     *
     * @return error message, or empty string if none
     */
    public static String getLastError() {
        String err = LAST_ERROR.get();
        if (err != null && !err.isEmpty()) return err;

        String nativeErr = WRAPPER.get().getLastError();
        return nativeErr != null ? nativeErr : "";
    }

    /**
     * Sets the last error string (Java-side).
     *
     * @param lastError error message (maybe null)
     * @since 1.0.0
     */
    public static void setLastError(String lastError) {
        LAST_ERROR.set(lastError);
    }

    // ---------- Internal helpers ----------

    private int resolveConfigNumericId(OpenccWrapper w) {
        int id = resolvedNumericId;
        if (id >= 0) return id;

        Objects.requireNonNull(configId, "configId"); // should never be null
        final int cached = CONFIG_ID_CACHE.computeIfAbsent(configId, w::configNameToId);
        if (cached < 0) {
            throw new IllegalStateException("Failed to resolve numeric OpenCC config id for " + configId);
        }

        resolvedNumericId = cached;
        return cached;
    }
}
