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
    EXT_B(0),
    EXT_C(1),
    EXT_D(2),
    EXT_E(3),
    EXT_F(4),
    EXT_G(5),
    EXT_H(6),
    EXT_I(7);

    private final int nativeValue;

    DeTofuLevel(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    int nativeValue() {
        return nativeValue;
    }
}
