package openccjni;

/**
 * Defines how custom dictionary entries are applied to a selected {@link DictSlot}.
 *
 * <p>The mode is applied once while an {@link OpenCC} instance is being
 * constructed. The resulting native dictionaries are immutable for the
 * lifetime of that instance.</p>
 *
 * @since 1.4.0
 */
public enum CustomDictMode {
    /**
     * Merges custom entries into the selected built-in dictionary slot.
     *
     * <p>Unrelated built-in entries remain available. When a custom source key
     * already exists, the custom target replaces the built-in target.</p>
     */
    Append(1),

    /**
     * Clears the selected built-in dictionary slot before adding custom entries.
     *
     * <p>Other slots in the conversion pipeline remain unchanged and may still
     * participate in later conversion rounds.</p>
     */
    Override(2);

    private final int nativeValue;

    CustomDictMode(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    int nativeValue() {
        return nativeValue;
    }
}