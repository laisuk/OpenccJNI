package openccjni;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * Supported OpenCC configuration names (conversion profiles).
     */
    private static final List<String> SUPPORTED_CONFIGS = Collections.unmodifiableList(Arrays.asList(
            "s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
            "t2tw", "tw2t", "t2twp", "tw2tp", "t2hk", "hk2t", "t2jp", "jp2t"
    ));

    /**
     * Supported OpenCC configurations as a set for quick lookup.
     */
    private static final Set<String> CONFIG_SET = Collections.unmodifiableSet(new HashSet<>(SUPPORTED_CONFIGS));

    /**
     * Default configuration if none is specified.
     */
    private static final String DEFAULT_CONFIG = "s2t";

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
     * Converts the given text using the specified configuration.
     *
     * @param input  the input string to convert (non-null; empty is allowed)
     * @param config the configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @return the converted string; if {@code config} is invalid, returns {@code input} unchanged
     * and records the error in {@link #getLastError()}
     * @since 1.0.0
     */
    public static String convert(String input, String config) {
        if (input == null) {
            setLastError("Input is null");
            return null;
        }
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            return input;
        }
        // Clear any previous Java-side error before invoking the native layer
        setLastError(null);
        return WRAPPER.get().convert(input, config, false);
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
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            return input;
        }
        // Clear any previous Java-side error before invoking the native layer
        setLastError(null);
        return WRAPPER.get().convert(input, config, punctuation);
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
     *         {@code false} otherwise
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
     * The current configuration used by this instance.
     */
    private String config;

    /**
     * Creates an {@code OpenCC} instance with the default configuration ({@code "s2t"}).
     *
     * @since 1.0.0
     */
    public OpenCC() {
        this.config = DEFAULT_CONFIG;
    }

    /**
     * Creates an {@code OpenCC} instance with a specified configuration.
     * If the provided config is invalid, {@code "s2t"} is used and an error is recorded.
     *
     * @param config the configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @since 1.0.0
     */
    public OpenCC(String config) {
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            config = DEFAULT_CONFIG;
        }
        this.config = config;
    }

    /**
     * Converts the input text using this instance's configuration.
     *
     * @param input the input string to convert (non-null; empty is allowed)
     * @return the converted string
     * @since 1.0.0
     */
    public String convert(String input) {
        if (input == null) {
            setLastError("Input is null");
            return "";
        }
        // Clear any previous Java-side error before invoking the native layer
        setLastError(null);
        return WRAPPER.get().convert(input, this.config, false);
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
        // Clear any previous Java-side error before invoking the native layer
        setLastError(null);
        return WRAPPER.get().convert(input, this.config, punctuation);
    }

    /**
     * Returns the current configuration key used by this instance.
     *
     * @return the configuration key (never null)
     * @since 1.0.0
     */
    public String getConfig() {
        return this.config;
    }

    /**
     * Updates this instance's configuration. If the provided key is invalid,
     * the configuration falls back to {@code "s2t"} and an error is recorded.
     *
     * @param config the configuration key (e.g., {@code "s2t"}, {@code "tw2s"})
     * @since 1.0.0
     */
    public void setConfig(String config) {
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            config = DEFAULT_CONFIG;
        }
        this.config = config;
    }

    /**
     * Returns an immutable list of supported configuration keys.
     *
     * @return an unmodifiable list of config keys
     * @since 1.0.0
     */
    public static List<String> getSupportedConfigs() {
        return SUPPORTED_CONFIGS;
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
