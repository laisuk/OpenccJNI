package openccjni;

import java.util.List;

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
            // Log and fallback to resources packaged in the JAR
//            System.err.println("File system 'OpenccWrapper' not found, use built-in resources: " + e);
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
    private OpenccConfig config;

    /**
     * Last error message encountered by OpenCC operations.
     */
    private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();

    // ---------- Static helpers ----------

    /**
     * Creates a new {@link OpenCC} instance with the given configuration.
     * If the provided config is invalid, {@code "s2t"} is used instead.
     *
     * @param config the configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @return a new {@link OpenCC} instance using the provided (or defaulted) config
     * @since 1.0.0
     */
    @SuppressWarnings("unused")
    public static OpenCC fromConfig(String config) {
        return new OpenCC(config);
    }

    /**
     * Creates a new {@link OpenCC} instance with the specified configuration.
     *
     * <p>This overload accepts a strongly-typed {@link OpenccConfig} value and
     * therefore avoids runtime string parsing and validation.</p>
     *
     * <p>If {@code configId} is {@code null}, the default configuration
     * ({@code "s2t"}) is used and an error is recorded via
     * {@link OpenCC#getLastError()}.</p>
     *
     * @param configId the OpenCC configuration identifier; may be {@code null}
     * @return a new {@link OpenCC} instance using the specified (or defaulted)
     * configuration
     * @since 1.0.0
     */
    public static OpenCC fromConfig(OpenccConfig configId) {
        return new OpenCC(configId);
    }

    /**
     * Converts the given text using the specified configuration.
     *
     * @param input  the input string to convert (non-null; empty is allowed)
     * @param config the configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @return the converted string; if {@code config} is invalid, returns {@code input} unchanged
     * and records the error in {@link #getLastError()}
     * @since 1.0.0
     */
    public static String convert(String input, String config) {
        return convert(input, config, false);
    }

    /**
     * Converts the given text using the specified configuration, optionally converting punctuation.
     *
     * @param input       the input string to convert (non-null; empty is allowed)
     * @param config      the configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @param punctuation whether to convert punctuation as well
     * @return the converted string; if {@code config} is invalid, returns {@code input} unchanged
     * and records the error in {@link #getLastError()}
     * @since 1.0.0
     */
    public static String convert(String input, String config, boolean punctuation) {
        if (input == null) {
            setLastError("Input is null");
            return null;
        }

        OpenccConfig cfg = OpenccConfig.tryParse(config);
        if (cfg == null) {
            setLastError("Invalid config: " + config);
            return input;
        }

        setLastError(null);
        return WRAPPER.get().convert(input, cfg.toCanonicalName(), punctuation);
    }

    /**
     * Converts the given text using the specified OpenCC configuration.
     *
     * <p>This overload accepts a strongly-typed {@link OpenccConfig} value and
     * provides a fast path that avoids runtime string parsing and validation.
     * It is intended for performance-sensitive code paths such as batch
     * processing, document conversion, or repeated conversions with a
     * known configuration.</p>
     *
     * <p>If {@code input} is {@code null}, this method returns {@code null} and
     * records an error. If {@code configId} is {@code null}, the input is returned
     * unchanged and an error is recorded via {@link #getLastError()}.</p>
     *
     * <p>This method does not perform any fallback to the default configuration.
     * Callers that require tolerant behavior should use the string-based
     * {@code convert(String, String, boolean)} overload instead.</p>
     *
     * @param input       the input string to convert; may be {@code null}
     * @param configId    the OpenCC configuration identifier; may be {@code null}
     * @param punctuation whether to convert punctuation as well
     * @return the converted string, {@code null} if {@code input} is {@code null},
     * or the original input if {@code configId} is {@code null}
     * @since 1.0.0
     */
    public static String convert(String input, OpenccConfig configId, boolean punctuation) {
        if (input == null) {
            setLastError("Input is null");
            return null;
        }
        if (configId == null) {
            setLastError("Config is null");
            return input;
        }
        setLastError(null);
        return WRAPPER.get().convert(input, configId.toCanonicalName(), punctuation);
    }

    /**
     * Checks whether the provided text contains Chinese characters.
     *
     * <p><b>Note:</b> The exact return codes are determined by the native C API.
     * Typically, {@code 0} indicates "not detected", and non-zero indicates the presence
     * (or classification) of Chinese characters.
     *
     * @param text the text to check (maybe null or empty)
     * @return an integer flag from the native layer; {@code 0} if none detected
     * @since 1.0.0
     */
    public static int zhoCheck(String text) {
        if (text == null || text.isEmpty()) {
            // No operation performed, clear previous error
            setLastError(null);
            return 0;
        }
        // Clear any previous Java-side error before invoking the native layer
        setLastError(null);
        return WRAPPER.get().zhoCheck(text);
    }

    /**
     * Checks whether global parallel conversion mode is currently enabled.
     *
     * <p>This flag is shared across all {@code OpenCC} instances in the JVM.
     * When enabled, conversion operations may use multiple threads to process
     * large texts or documents, improving performance on multicore systems.</p>
     *
     * @return {@code true} if parallel mode is enabled globally;
     * {@code false} otherwise
     * @since 1.0.2
     */
    public static boolean isParallel() {
        return WRAPPER.get().isParallel();
    }

    /**
     * Enables or disables the global parallel conversion mode.
     *
     * <p>This setting applies to all {@code OpenCC} instances in the JVM.
     * Mixing parallel and non-parallel modes across instances is not supported
     * and may lead to unstable behavior.</p>
     *
     * <p>When set to {@code true}, conversion operations may be executed in
     * parallel across multiple threads. For smaller inputs, parallel mode
     * may introduce overhead and is not always beneficial.</p>
     *
     * @param isParallel {@code true} to enable parallel processing globally,
     *                   {@code false} to disable it
     * @since 1.0.2
     */
    public static void setParallel(boolean isParallel) {
        WRAPPER.get().setParallel(isParallel);
    }

    // ---------- Instance API ----------

    /**
     * Creates an {@code OpenCC} instance with the default configuration ({@code "s2t"}).
     *
     * @since 1.0.0
     */
    public OpenCC() {
        this.config = OpenccConfig.defaultConfig();
    }

    /**
     * Creates an {@code OpenCC} instance with the specified configuration string.
     *
     * <p>The provided configuration string is parsed in a case-insensitive manner.
     * Both canonical OpenCC names (for example {@code "s2t"}, {@code "t2twp"})
     * and enum-style names (for example {@code "S2T"}, {@code "T2TWP"}) are accepted.</p>
     *
     * <p>If the configuration string is {@code null}, empty, or does not correspond
     * to any supported configuration, the default configuration
     * ({@code "s2t"}) is used and an error is recorded via
     * {@link OpenCC#getLastError()}.</p>
     *
     * @param config the configuration key; may be {@code null}
     * @since 1.0.0
     */
    public OpenCC(String config) {
        OpenccConfig parsed = OpenccConfig.tryParse(config);
        if (parsed == null) {
            setLastError("Invalid config: " + config);
            parsed = OpenccConfig.defaultConfig();
        }
        this.config = parsed;
    }

    /**
     * Creates an {@code OpenCC} instance with the specified configuration identifier.
     *
     * <p>This constructor accepts a strongly-typed {@link OpenccConfig} value and
     * therefore avoids runtime string parsing and validation.</p>
     *
     * <p>If {@code configId} is {@code null}, the default configuration
     * ({@code "s2t"}) is used and an error is recorded via
     * {@link OpenCC#getLastError()}.</p>
     *
     * @param configId the OpenCC configuration identifier; may be {@code null}
     * @since 1.0.0
     */
    public OpenCC(OpenccConfig configId) {
        if (configId == null) {
            setLastError("Config is null");
            configId = OpenccConfig.defaultConfig();
        }
        this.config = configId;
    }

    /**
     * Converts the input text using this instance's configuration.
     *
     * @param input the input string to convert (non-null; empty is allowed)
     * @return the converted string
     * @since 1.0.0
     */
    public String convert(String input) {
        return convert(input, false);
    }

    /**
     * Converts the input text using this instance's configuration,
     * optionally converting punctuation.
     *
     * @param input       the input string to convert (non-null; empty is allowed)
     * @param punctuation whether to convert punctuation as well
     * @return the converted string
     * @since 1.0.0
     */
    public String convert(String input, boolean punctuation) {
        if (input == null) {
            setLastError("Input is null");
            return "";
        }
        setLastError(null);
        return WRAPPER.get().convert(input, this.config.toCanonicalName(), punctuation);
    }

    /**
     * Returns the canonical configuration name used by this {@code OpenCC} instance.
     *
     * <p>The returned value is the lowercase OpenCC configuration key
     * (for example {@code "s2t"}, {@code "t2twp"}), suitable for use in
     * native calls, logging, CLI output, or configuration serialization.</p>
     *
     * <p>This method never returns {@code null}. The value always reflects
     * the effective configuration currently in use by this instance.</p>
     *
     * @return the canonical OpenCC configuration name (never {@code null})
     * @since 1.0.0
     */
    public String getConfig() {
        return this.config.toCanonicalName();
    }

    /**
     * Returns the OpenCC configuration identifier used by this instance.
     *
     * <p>This method provides access to the strongly-typed
     * {@link OpenccConfig} value and avoids string-based configuration
     * handling. It is the preferred accessor for programmatic use.</p>
     *
     * <p>The returned value is never {@code null}.</p>
     *
     * @return the current {@link OpenccConfig} identifier (never {@code null})
     * @since 1.0.0
     */
    public OpenccConfig getConfigId() {
        return this.config;
    }

    /**
     * Updates this instance's configuration using a configuration string.
     *
     * <p>The provided configuration string is parsed in a case-insensitive manner.
     * Both canonical OpenCC names (for example {@code "s2t"}, {@code "t2twp"})
     * and enum-style names (for example {@code "S2T"}, {@code "T2TWP"}) are accepted.</p>
     *
     * <p>If the configuration string is {@code null}, empty, or does not correspond
     * to any supported configuration, the default configuration
     * ({@code "s2t"}) is used and an error is recorded via
     * {@link OpenCC#getLastError()}.</p>
     *
     * @param config the configuration key; may be {@code null}
     * @since 1.0.0
     */
    public void setConfig(String config) {
        OpenccConfig parsed = OpenccConfig.tryParse(config);
        if (parsed == null) {
            setLastError("Invalid config: " + config);
            parsed = OpenccConfig.defaultConfig();
        }
        this.config = parsed;
    }

    /**
     * Updates this instance's configuration using a configuration identifier.
     *
     * <p>This overload accepts a strongly-typed {@link OpenccConfig} value and
     * avoids runtime string parsing and validation.</p>
     *
     * <p>If {@code configId} is {@code null}, the default configuration
     * ({@code "s2t"}) is used and an error is recorded via
     * {@link OpenCC#getLastError()}.</p>
     *
     * @param configId the OpenCC configuration identifier; may be {@code null}
     * @since 1.0.0
     */
    public void setConfig(OpenccConfig configId) {
        if (configId == null) {
            setLastError("Config is null");
            configId = OpenccConfig.defaultConfig();
        }
        this.config = configId;
    }

    /**
     * Returns an immutable list of supported configuration keys.
     *
     * @return an unmodifiable list of config keys
     * @since 1.0.0
     */
    public static List<String> getSupportedConfigs() {
        // single source: enum
        return OpenccConfig.supportedCanonicalNames();
    }

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
     * Sets the last error string.
     * <p><b>Note:</b> Intended primarily for internal use; left public for flexibility.
     *
     * @param lastError the error message to record (maybe {@code null})
     * @since 1.0.0
     */
    public static void setLastError(String lastError) {
        LAST_ERROR.set(lastError);
    }
}
