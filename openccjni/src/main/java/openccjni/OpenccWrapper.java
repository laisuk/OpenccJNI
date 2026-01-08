package openccjni;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

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

    /**
     * Converts text using a numeric config id and punctuation setting.
     *
     * <p>Native contract: if {@code configId} is invalid, this function still returns
     * a newly allocated UTF-8 error message string (e.g. {@code "Invalid config: 123"})
     * and also sets the same message as last error.</p>
     *
     * @param instance    pointer to native OpenCC instance
     * @param input       UTF-8 encoded input string
     * @param configId    numeric config id (opencc_config_t)
     * @param punctuation whether to convert punctuation
     * @return UTF-8 encoded result (or error string for invalid config), or {@code null}
     * only if {@code instance} or {@code input} is null, or if allocation fails
     * @since opencc-fmmseg-capi v0.8.4
     */
    private native byte[] opencc_convert_cfg(long instance, byte[] input, int configId, boolean punctuation);

    /**
     * Converts a canonical OpenCC config name (UTF-8) to its numeric config id.
     *
     * @param nameUtf8 UTF-8 encoded canonical config name (e.g. {@code "s2twp"})
     * @return numeric config id (opencc_config_t), or {@code -1} if invalid / null
     * @since opencc-fmmseg-capi v0.8.4
     */
    private native int opencc_config_name_to_id(byte[] nameUtf8);

    /**
     * Converts a numeric OpenCC config id to its canonical config name (UTF-8).
     *
     * @param configId numeric config id (opencc_config_t)
     * @return UTF-8 encoded canonical config name, or {@code null} if invalid
     * @since opencc-fmmseg-capi v0.8.4
     */
    private native byte[] opencc_config_id_to_name(int configId);

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    /**
     * Pointer to the underlying native OpenCC instance.
     */
    private long instance;

    // No configuration list; higher-level classes should validate configs.

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
            String err = opencc_last_error();
            throw new RuntimeException("Failed to create OpenCC instance: " + (err != null ? err : ""));
        }
    }

    // ------------------------------------------------------------------------
    // Private methods
    // ------------------------------------------------------------------------

    /**
     * Ensures that the native instance is still open.
     *
     * @throws IllegalStateException if the underlying native instance has been closed
     */
    private void ensureOpen() {
        if (instance == 0) {
            throw new IllegalStateException("OpenCC instance is closed");
        }
    }

    // ------------------------------------------------------------------------
    // Public methods
    // ------------------------------------------------------------------------

    /**
     * Converts the given input string using a specified configuration.
     *
     * @param input       input text
     * @param config      conversion config key
     * @param punctuation whether to convert punctuation as well
     * @return the converted string
     * @throws RuntimeException if conversion fails
     */
    public String convert(String input, String config, boolean punctuation) {
        ensureOpen();
        Objects.requireNonNull(input, "input cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        if (input.isEmpty()) return "";

        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        byte[] configBytes = config.getBytes(StandardCharsets.UTF_8);
        byte[] rawOutput = opencc_convert(instance, inputBytes, configBytes, punctuation);

        if (rawOutput == null) {
            throw new RuntimeException("Conversion failed: " + getLastError());
        }

        return new String(rawOutput, StandardCharsets.UTF_8);
    }

    /**
     * Converts the given input string using a numeric OpenCC configuration id.
     *
     * <p>This is a fast path for callers who already resolved a config name to an id
     * (for example via {@link #configNameToId(String)}). It avoids passing config strings
     * across JNI and lets the native layer select configs by numeric id.</p>
     *
     * <p>Contract (native side):</p>
     * <ul>
     *   <li>If {@code instance} or {@code input} is null, native returns {@code null}.</li>
     *   <li>If {@code configId} is invalid, native returns a newly allocated UTF-8 error string
     *       like {@code "Invalid config: 123"} and also sets it as the last error.</li>
     *   <li>Native returns {@code null} only for fatal errors (e.g. OOM / null inputs).</li>
     * </ul>
     *
     * @param input       input text (non-null)
     * @param configId    numeric OpenCC config id (opencc_config_t)
     * @param punctuation whether to convert punctuation as well
     * @return converted text; for invalid {@code configId}, returns the native error string
     * @throws RuntimeException if the native conversion returns {@code null} unexpectedly
     *                          (typically OOM or a fatal native error)
     * @since opencc-fmmseg-capi v0.8.4
     */
    public String convertCfg(String input, int configId, boolean punctuation) {
        ensureOpen();
        Objects.requireNonNull(input, "input cannot be null");
        if (input.isEmpty()) return "";

        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        byte[] rawOutput = opencc_convert_cfg(instance, inputBytes, configId, punctuation);

        if (rawOutput == null) {
            // per contract: NULL only on fatal errors (NULL input/instance or OOM)
            throw new RuntimeException("Conversion failed: " + getLastError());
        }
        return new String(rawOutput, StandardCharsets.UTF_8);
    }

    /**
     * Resolves a canonical OpenCC configuration name to its numeric configuration id.
     *
     * <p>This method is tolerant: it returns {@code -1} for null/blank/unknown inputs
     * and never throws.</p>
     *
     * <p>Input is expected to be an OpenCC canonical name (lowercase), e.g. {@code "s2twp"}.
     * If you want to accept enum-style or mixed-case inputs, normalize using
     * {@link OpenccConfig#toCanonicalNameOrNull(String)} first.</p>
     *
     * @param canonicalName canonical config name (e.g. {@code "s2twp"}); may be null
     * @return numeric config id (opencc_config_t), or {@code -1} if invalid/unknown
     * @since opencc-fmmseg-capi v0.8.4
     */
    public int configNameToId(String canonicalName) {
        ensureOpen();
        if (canonicalName == null) return -1;
        String trimmed = canonicalName.trim();
        if (trimmed.isEmpty()) return -1;
        return opencc_config_name_to_id(trimmed.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resolves an {@link OpenccConfig} enum value to its numeric configuration id.
     *
     * @param configId config enum; may be null
     * @return numeric config id (opencc_config_t), or {@code -1} if {@code configId} is null
     * @since opencc-fmmseg-capi v0.8.4
     */
    public int configNameToId(OpenccConfig configId) {
        ensureOpen();
        if (configId == null) return -1;
        return opencc_config_name_to_id(configId.toCanonicalName().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Resolves a numeric OpenCC configuration id to its canonical configuration name.
     *
     * @param configId numeric config id (opencc_config_t)
     * @return canonical config name (e.g. {@code "s2twp"}), or {@code null} if {@code configId} is invalid
     * @since opencc-fmmseg-capi v0.8.4
     */
    public String configIdToName(int configId) {
        ensureOpen();
        byte[] name = opencc_config_id_to_name(configId);
        return name == null ? null : new String(name, StandardCharsets.UTF_8);
    }

    /**
     * Returns whether this instance is operating in parallel mode.
     *
     * @return {@code true} if parallel mode is enabled, {@code false} otherwise
     */
    public boolean isParallel() {
        ensureOpen();
        return opencc_get_parallel(instance);
    }

    /**
     * Sets whether this instance should operate in parallel mode.
     *
     * @param isParallel {@code true} to enable parallel mode, {@code false} to disable
     */
    public void setParallel(boolean isParallel) {
        ensureOpen();
        opencc_set_parallel(instance, isParallel);
    }

    /**
     * Checks whether the input string contains Chinese characters.
     *
     * @param input the text to check
     * @return non-zero if Chinese characters are detected, 0 otherwise
     */
    public int zhoCheck(String input) {
        ensureOpen();
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null");
        }
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        return opencc_zho_check(instance, inputBytes);
    }

    /**
     * Returns the last error message reported by the native library.
     *
     * @return error message, or empty string if none
     */
    public String getLastError() {
        ensureOpen();
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
