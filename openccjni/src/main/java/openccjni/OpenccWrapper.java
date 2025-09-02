package openccjni;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Low-level JNI wrapper around the native OpenCC + FMMSEG C API.
 * <p>
 * This class provides direct bindings to the native functions for text conversion,
 * configuration, and parallelism. Typically, you should use {@link OpenCC} for
 * a higher-level and thread-safe interface. This wrapper is mainly intended for
 * internal use by {@code OpenCC}, though it can be used directly if needed.
 * </p>
 *
 * <h2>Usage example:</h2>
 * <pre>{@code
 * try (OpenccWrapper wrapper = new OpenccWrapper()) {
 *     String result = wrapper.convert("汉字", "s2t", false);
 *     System.out.println(result);
 * }
 * }</pre>
 */
public class OpenccWrapper implements AutoCloseable {

    // ------------------------------------------------------------------------
    // Native method declarations (implemented in the JNI library)
    // ------------------------------------------------------------------------

    /**
     * Creates a new native OpenCC instance.
     */
    private native long opencc_new();

    /**
     * Converts text using the given config and punctuation setting.
     *
     * @param instance    pointer to native OpenCC instance
     * @param input       UTF-8 encoded input string
     * @param config      UTF-8 encoded config key (e.g., "s2t")
     * @param punctuation whether to convert punctuation
     * @return UTF-8 encoded result, or {@code null} if conversion failed
     */
    private native byte[] opencc_convert(long instance, byte[] input, byte[] config, boolean punctuation);

    /**
     * Returns whether parallel mode is enabled for this instance.
     */
    private native boolean opencc_get_parallel(long instance);

    /**
     * Enables or disables parallel mode for this instance.
     */
    private native void opencc_set_parallel(long instance, boolean is_parallel);

    /**
     * Checks whether the input text contains Chinese characters.
     *
     * @param instance pointer to native OpenCC instance
     * @param input    UTF-8 encoded input string
     * @return non-zero if Chinese text is detected, 0 otherwise
     */
    private native int opencc_zho_check(long instance, byte[] input);

    /**
     * Deletes and frees the native OpenCC instance.
     */
    private native void opencc_delete(long instance);

    /**
     * Retrieves the last error message from the native side.
     */
    private native String opencc_last_error();

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /**
     * Pointer to the underlying native OpenCC instance.
     */
    private long instance;

    /**
     * List of supported configuration names (conversion profiles).
     */
    private static final List<String> configList = Arrays.asList(
            "s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
            "t2tw", "t2twp", "t2hk", "tw2t", "tw2tp", "hk2t", "t2jp", "jp2t"
    );

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Creates a new {@code OpenccWrapper}, allocating a native OpenCC instance.
     *
     * @throws RuntimeException if the native instance could not be created
     */
    public OpenccWrapper() {
        instance = opencc_new();
        if (instance == 0) {
            throw new RuntimeException("Failed to create OpenCC instance: " + getLastError());
        }
    }

    // ------------------------------------------------------------------------
    // Public methods
    // ------------------------------------------------------------------------

    /**
     * Converts the given input string using a specified configuration.
     *
     * @param input       input text
     * @param config      conversion config key (if invalid, defaults to "s2t")
     * @param punctuation whether to convert punctuation as well
     * @return the converted string
     * @throws RuntimeException if conversion fails
     */
    public String convert(String input, String config, boolean punctuation) {
        if (input.isEmpty()) return "";
        if (!configList.contains(config)) config = "s2t";

        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        byte[] configBytes = config.getBytes(StandardCharsets.UTF_8);
        byte[] rawOutput = opencc_convert(instance, inputBytes, configBytes, punctuation);

        if (rawOutput == null) {
            throw new RuntimeException("Conversion failed: " + getLastError());
        }

        return new String(rawOutput, StandardCharsets.UTF_8);
    }

    /**
     * Returns whether this instance is operating in parallel mode.
     *
     * @return {@code true} if parallel mode is enabled, {@code false} otherwise
     */
    @SuppressWarnings("unused")
    public boolean isParallel() {
        return opencc_get_parallel(instance);
    }

    /**
     * Sets whether this instance should operate in parallel mode.
     *
     * @param isParallel {@code true} to enable parallel mode, {@code false} to disable
     */
    @SuppressWarnings("unused")
    public void setParallel(boolean isParallel) {
        opencc_set_parallel(instance, isParallel);
    }

    /**
     * Checks whether the input string contains Chinese characters.
     *
     * @param input the text to check
     * @return non-zero if Chinese characters are detected, 0 otherwise
     */
    public int zhoCheck(String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        return opencc_zho_check(instance, inputBytes);
    }

    /**
     * Returns the last error message reported by the native library.
     *
     * @return error message, or empty string if none
     */
    public String getLastError() {
        final String lastError = opencc_last_error(); // native
        return lastError != null ? lastError : "";
    }

    /**
     * Releases the underlying native resources associated with this instance.
     * <p>
     * After calling {@code close()}, this instance should no longer be used.
     * </p>
     */
    @Override
    public void close() {
        if (instance != 0) {
            opencc_delete(instance);
            instance = 0;
        }
    }
}
