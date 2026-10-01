package openccjni;

/**
 * DeTofu fallback threshold.
 *
 * <p>The selected level is inclusive. For example, {@link #EXT_B}
 * applies mappings for ExtB through ExtI, while {@link #EXT_I}
 * applies ExtI mappings only.</p>
 *
 * @since 1.4.0
 */
public enum DeTofuLevel {
    /**
     * Applies fallback mappings for CJK Extension B through Extension I, inclusive.
     */
    EXT_B(0),
    /**
     * Applies fallback mappings for CJK Extension C through Extension I, inclusive.
     */
    EXT_C(1),
    /**
     * Applies fallback mappings for CJK Extension D through Extension I, inclusive.
     */
    EXT_D(2),
    /**
     * Applies fallback mappings for CJK Extension E through Extension I, inclusive.
     */
    EXT_E(3),
    /**
     * Applies fallback mappings for CJK Extension F through Extension I, inclusive.
     */
    EXT_F(4),
    /**
     * Applies fallback mappings for CJK Extension G through Extension I, inclusive.
     */
    EXT_G(5),
    /**
     * Applies fallback mappings for CJK Extension H through Extension I, inclusive.
     */
    EXT_H(6),
    /**
     * Applies fallback mappings for CJK Extension I only.
     */
    EXT_I(7);

    private final int nativeValue;

    DeTofuLevel(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    int nativeValue() {
        return nativeValue;
    }
}
