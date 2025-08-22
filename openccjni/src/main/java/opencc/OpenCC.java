package opencc;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class OpenCC {
    static {
        System.loadLibrary("OpenccWrapper");
    }

    // Thread-local native wrapper for OpenCC (safe for parallel use)
    private static final ThreadLocal<OpenccWrapper> WRAPPER =
            ThreadLocal.withInitial(OpenccWrapper::new);

    // Supported OpenCC config names
    private static final Set<String> CONFIG_SET = new HashSet<>(Arrays.asList(
            "s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
            "t2tw", "t2twp", "t2hk", "tw2t", "tw2tp", "hk2t", "t2jp", "jp2t"
    ));

    // Default config
    private static final String DEFAULT_CONFIG = "s2t";
    private static String lastError;

    /**
     * Static utility method for one-off conversion with explicit config.
     */
    public static String convert(String input, String config) {
        if (!CONFIG_SET.contains(config)) {
            lastError = "Invalid config: " + config;
            return input;
        }
        return WRAPPER.get().convert(input, config, false);
    }

    /**
     * Static utility method for one-off conversion with explicit config.
     */
    public static String convert(String input, String config, boolean punctuation) {
        if (!CONFIG_SET.contains(config)) {
            lastError = "Invalid config: " + config;
            return input;
        }
        return WRAPPER.get().convert(input, config, punctuation);
    }

    /**
     * Static method to check if the text is Chinese.
     */
    public static int zhoCheck(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return WRAPPER.get().zhoCheck(text);
    }

    // ----- Instance Implementation -----

    // Instance-level immutable config
    private String config;

    // Constructor
    public OpenCC() {
        this.config = DEFAULT_CONFIG;
    }

    public OpenCC(String config) {
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            config = DEFAULT_CONFIG;
        }
        this.config = config;
    }

    /**
     * Instance method to convert using the current config.
     */
    public String convert(String input) {
        return WRAPPER.get().convert(input, this.config, false);
    }

    /**
     * Instance method to convert using the current config.
     */
    public String convert(String input, boolean punctuation) {
        return WRAPPER.get().convert(input, this.config, punctuation);
    }

    /**
     * Returns the current config.
     */
    public String getConfig() {
        return this.config;
    }

    /**
     * Set the current config.
     */
    public void setConfig(String config) {
        if (!CONFIG_SET.contains(config)) {
            setLastError("Invalid config: " + config);
            config = DEFAULT_CONFIG;
        }
        this.config = config;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        OpenCC.lastError = lastError;
    }
}
