package opencc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java binding for OpenCC (Open Chinese Convert).
 * <p>
 * Provides both static utility methods for one-off conversions and
 * instance methods with a configurable conversion profile.
 * This class loads its native JNI wrapper {@code OpenccWrapper}
 * and delegates text conversion to the underlying C API implementation.
 * </p>
 *
 * <h2>Usage examples:</h2>
 *
 * <pre>{@code
 * // Static one-off conversion
 * String output = OpenCC.convert("汉字", "s2t");
 *
 * // Instance-based conversion with persistent config
 * OpenCC cc = new OpenCC("tw2s");
 * String result = cc.convert("繁體字");
 * }</pre>
 */
public final class OpenCC {
    static {
        try {
            // Try normal search path (PATH, java.library.path, current dir, etc.)
            System.loadLibrary("OpenccWrapper");
        } catch (UnsatisfiedLinkError e) {
            // Fallback: extract from JAR resources and load all deps
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
    private static final Set<String> CONFIG_SET = new HashSet<>(Arrays.asList(
            "s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
            "t2tw", "t2twp", "t2hk", "tw2t", "tw2tp", "hk2t", "t2jp", "jp2t"
    ));

    /**
     * Default configuration if none is specified.
     */
    private static final String DEFAULT_CONFIG = "s2t";

    /**
     * Last error message encountered by OpenCC operations.
     */
    private static String lastError;

    // ----- Static Utility Methods -----

    /**
     * Converts input text using a specified configuration.
     *
     * @param input  the input string to convert
     * @param config the configuration key (e.g., "s2t", "tw2s")
     * @return the converted string, or original input if config is invalid
     */
    public static String convert(String input, String config) {
        if (!CONFIG_SET.contains(config)) {
            lastError = "Invalid config: " + config;
            return input;
        }
        return WRAPPER.get().convert(input, config, false);
    }

    /**
     * Converts input text using a specified configuration, with optional punctuation conversion.
     *
     * @param input       the input string to convert
     * @param config      the configuration key (e.g., "s2t", "tw2s")
     * @param punctuation whether to convert punctuation as well
     * @return the converted string, or original input if config is invalid
     */
    public static String convert(String input, String config, boolean punctuation) {
        if (!CONFIG_SET.contains(config)) {
            lastError = "Invalid config: " + config;
            return input;
        }
        return WRAPPER.get().convert(input, config, punctuation);
    }

    /**
     * Checks whether the given text contains Chinese characters.
     *
     * @param text the text to check
     * @return non-zero if Chinese characters are detected, 0 otherwise
     */
    public static int zhoCheck(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return WRAPPER.get().zhoCheck(text);
    }

    // ----- Instance Implementation -----

    /**
     * The current configuration used by this instance.
     */
    private String config;

    /**
     * Creates an {@code OpenCC} instance with the default configuration ("s2t").
     */
    public OpenCC() {
        this.config = DEFAULT_CONFIG;
    }

    /**
     * Creates an {@code OpenCC} instance with a specified configuration.
     *
     * @param config the configuration key (if invalid, defaults to "s2t")
     */
    public OpenCC(String config) {
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            config = DEFAULT_CONFIG;
        }
        this.config = config;
    }

    /**
     * Converts input text using this instance's configuration.
     *
     * @param input the input string to convert
     * @return the converted string
     */
    public String convert(String input) {
        return WRAPPER.get().convert(input, this.config, false);
    }

    /**
     * Converts input text using this instance's configuration,
     * with optional punctuation conversion.
     *
     * @param input       the input string to convert
     * @param punctuation whether to convert punctuation as well
     * @return the converted string
     */
    public String convert(String input, boolean punctuation) {
        return WRAPPER.get().convert(input, this.config, punctuation);
    }

    /**
     * Returns the current configuration for this instance.
     *
     * @return the configuration key
     */
    public String getConfig() {
        return this.config;
    }

    /**
     * Updates the configuration for this instance.
     *
     * @param config the configuration key (if invalid, defaults to "s2t")
     */
    public void setConfig(String config) {
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            config = DEFAULT_CONFIG;
        }
        this.config = config;
    }

    /**
     * Returns the list of supported configuration keys.
     *
     * @return immutable list of supported configs
     */
    public static List<String> getSupportedConfigs() {
        return List.of("s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
                "t2tw", "tw2t", "t2twp", "tw2tp", "t2hk", "hk2t", "t2jp", "jp2t");
    }

    /**
     * Returns the last error message encountered.
     *
     * @return last error as string
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Sets the last error message (used internally).
     *
     * @param lastError the error message
     */
    public void setLastError(String lastError) {
        OpenCC.lastError = lastError;
    }
}
