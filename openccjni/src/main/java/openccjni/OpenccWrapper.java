package openccjni;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    // ------------------------------------------------------------------------
    // Native method declarations (implemented in the JNI library)
    // ------------------------------------------------------------------------

    /**
     * Returns the native OpenCC-FMMSEG C ABI version number.
     *
     * <p>This value only changes when the C ABI is broken and can be used
     * for runtime compatibility checks.</p>
     */
    private static native int opencc_abi_number();

    /**
     * Returns the native OpenCC-FMMSEG version string.
     *
     * <p>The returned string is owned by the native library and is valid
     * for the lifetime of the process.</p>
     *
     * <p>Example: {@code "0.9.2"}</p>
     */
    private static native String opencc_version_string();

    /**
     * Creates a new native OpenCC instance.
     */
    private native long opencc_new();

    /**
     * Creates a native instance from flattened custom dictionary specifications.
     * All arrays are borrowed only for the duration of this JNI call.
     */
    private native long opencc_new_custom(
            int[] slots,
            int[] modes,
            int[] pairCounts,
            byte[][] sourcesUtf8,
            byte[][] targetsUtf8
    );

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
     * Clears the native last-error state.
     */
    private native void opencc_clear_last_error();

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
     * @since 1.2.0
     */
    private native byte[] opencc_convert_cfg(long instance, byte[] input, int configId, boolean punctuation);

    /**
     * Converts a canonical OpenCC config name (UTF-8) to its numeric config id.
     *
     * @param nameUtf8 UTF-8 encoded canonical config name (e.g. {@code "s2twp"})
     * @return numeric config id (opencc_config_t), or {@code -1} if invalid / null
     * @since 1.2.0
     */
    private native int opencc_config_name_to_id(byte[] nameUtf8);

    /**
     * Converts a numeric OpenCC config id to its canonical config name (UTF-8).
     *
     * @param configId numeric config id (opencc_config_t)
     * @return UTF-8 encoded canonical config name, or {@code null} if invalid
     * @since 1.2.0
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

    /**
     * Creates a native wrapper with immutable in-memory custom dictionaries.
     *
     * <p>The supplied specifications and UTF-8 strings are copied during
     * construction. An empty list is equivalent to the default wrapper.</p>
     *
     * @param specs low-level custom dictionary specifications; must not be
     *              {@code null} or contain {@code null}
     * @throws NullPointerException if the list, a specification, its pair list,
     *                              or a pair is {@code null}
     * @throws IllegalArgumentException if a source or target contains an embedded
     *                                  NUL character
     * @throws RuntimeException if the native instance cannot be created
     * @since 1.4.0
     */
    public OpenccWrapper(List<OpenccCustomDictSpec> specs) {
        Objects.requireNonNull(specs, "specs cannot be null");

        int specCount = specs.size();
        int totalPairs = 0;

        for (OpenccCustomDictSpec spec : specs) {
            Objects.requireNonNull(spec, "spec cannot be null");
            totalPairs = Math.addExact(totalPairs, spec.getPairs().size());
        }

        int[] slots = new int[specCount];
        int[] modes = new int[specCount];
        int[] pairCounts = new int[specCount];
        byte[][] sourcesUtf8 = new byte[totalPairs][];
        byte[][] targetsUtf8 = new byte[totalPairs][];

        int pairIndex = 0;

        for (int i = 0; i < specCount; i++) {
            OpenccCustomDictSpec spec = specs.get(i);

            slots[i] = spec.getSlot();
            modes[i] = spec.getMode();
            pairCounts[i] = spec.getPairs().size();

            for (OpenccCustomPair pair : spec.getPairs()) {
                sourcesUtf8[pairIndex] =
                        pair.getSource().getBytes(StandardCharsets.UTF_8);
                targetsUtf8[pairIndex] =
                        pair.getTarget().getBytes(StandardCharsets.UTF_8);
                pairIndex++;
            }
        }

        instance = opencc_new_custom(
                slots,
                modes,
                pairCounts,
                sourcesUtf8,
                targetsUtf8
        );

        if (instance == 0) {
            String err = opencc_last_error();
            throw new RuntimeException(
                    "Failed to create custom OpenCC instance: "
                            + (err != null ? err : "")
            );
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
     * Returns the native OpenCC-FMMSEG C ABI version number.
     *
     * <p>This value is intended for runtime compatibility checks.
     * It only changes when the native C ABI is broken.</p>
     *
     * @return the native C ABI version number
     * @since 1.2.0
     */
    public static int getAbiNumber() {
        return opencc_abi_number();
    }

    /**
     * Returns the native OpenCC-FMMSEG version string.
     *
     * <p>The returned string is owned by the native library and is valid
     * for the lifetime of the process.</p>
     *
     * <p>Example: {@code "0.9.2"}</p>
     *
     * @return the native OpenCC-FMMSEG version string
     * @since 1.2.0
     */
    public static String getVersionString() {
        String v = opencc_version_string();
        return v != null ? v : "";
    }

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
     * @since 1.2.0
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
     * <p>This method is tolerant while the wrapper is open: it returns {@code -1}
     * for null, blank, or unknown inputs.</p>
     *
     * <p>Input is expected to be an OpenCC canonical name (lowercase), e.g. {@code "s2twp"}.
     * If you want to accept enum-style or mixed-case inputs, normalize using
     * {@link OpenccConfig#toCanonicalNameOrNull(String)} first.</p>
     *
     * @param canonicalName canonical config name (e.g. {@code "s2twp"}); may be null
     * @return numeric config id (opencc_config_t), or {@code -1} if invalid/unknown
     * @throws IllegalStateException if this wrapper has been closed
     * @since 1.2.0
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
     * @since 1.2.0
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
     * @since 1.2.0
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
        if (lastError == null || "No error".equals(lastError)) {
            return "";
        }
        return lastError;
    }

    /**
     * Clears the native last error so future reads reflect only new failures.
     *
     * @since 1.2.2
     */
    public void clearLastError() {
        ensureOpen();
        opencc_clear_last_error();
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

    // ------------------------------------------------------------------------
    // Custom dictionary value types
    // ------------------------------------------------------------------------

    /**
     * One low-level source-to-target custom dictionary mapping.
     *
     * <p>Most applications should use {@link CustomDictSpec} and let
     * {@link OpenCC} parse dictionary files. This type is intended for direct
     * {@code OpenccWrapper} integrations.</p>
     *
     * @since 1.4.0
     */
    public static final class OpenccCustomPair {
        private final String source;
        private final String target;

        /**
         * Creates a custom mapping pair.
         *
         * @param source source dictionary key; must not be {@code null}
         * @param target replacement text; must not be {@code null}
         * @throws NullPointerException if either argument is {@code null}
         */
        public OpenccCustomPair(String source, String target) {
            this.source = Objects.requireNonNull(source, "source cannot be null");
            this.target = Objects.requireNonNull(target, "target cannot be null");
        }

        /**
         * Returns the source dictionary key.
         *
         * @return source dictionary key
         */
        public String getSource() {
            return source;
        }

        /**
         * Returns the replacement text.
         *
         * @return replacement text
         */
        public String getTarget() {
            return target;
        }
    }

    /**
     * One low-level custom dictionary operation using native slot and mode values.
     *
     * <p>The pair list is defensively copied and exposed as an unmodifiable list.
     * Prefer {@link CustomDictSpec} for the strongly typed, file-based API.</p>
     *
     * @since 1.4.0
     */
    public static final class OpenccCustomDictSpec {
        private final int slot;
        private final int mode;
        private final List<OpenccCustomPair> pairs;

        /**
         * Creates a low-level custom dictionary specification.
         *
         * @param slot native {@code opencc_dict_slot_t} value
         * @param mode native {@code opencc_custom_dict_mode_t} value
         * @param pairs mappings to apply; must not be {@code null}
         * @throws NullPointerException if {@code pairs} is {@code null}
         */
        public OpenccCustomDictSpec(
                int slot,
                int mode,
                List<OpenccCustomPair> pairs) {
            this.slot = slot;
            this.mode = mode;
            this.pairs = Collections.unmodifiableList(
                    new ArrayList<>(Objects.requireNonNull(pairs, "pairs cannot be null"))
            );
        }

        /**
         * Returns the native dictionary slot value.
         *
         * @return native dictionary slot value
         */
        public int getSlot() {
            return slot;
        }

        /**
         * Returns the native custom dictionary mode value.
         *
         * @return native custom dictionary mode value
         */
        public int getMode() {
            return mode;
        }

        /**
         * Returns the immutable custom mapping list.
         *
         * @return immutable custom mapping list
         */
        public List<OpenccCustomPair> getPairs() {
            return pairs;
        }
    }
}