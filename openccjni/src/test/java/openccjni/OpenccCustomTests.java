package openccjni;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenccCustomTests {

    private static final Logger LOGGER = Logger.getLogger(OpenccCustomTests.class.getName());

    // opencc_dict_slot_t values from opencc_fmmseg_capi.h.
    private static final int ST_PHRASES = 2;
    private static final int TS_PHRASES = 4;

    // opencc_custom_dict_mode_t values from opencc_fmmseg_capi.h.
    private static final int APPEND = 1;
    private static final int OVERRIDE = 2;

    @Test
    void appendCustomPhraseIsUsedDuringConversion() {
        OpenccWrapper.OpenccCustomDictSpec spec = spec(
                ST_PHRASES,
                APPEND,
                pair("测试术语", "自訂結果")
        );

        try (OpenccWrapper opencc = customWrapper(spec)) {
            assertEquals("這是自訂結果", opencc.convert("这是测试术语", "s2t", false));
        }
    }

    @Test
    void multipleSpecsKeepTheirOwnFlattenedPairRanges() {
        OpenccWrapper.OpenccCustomDictSpec stSpec = spec(
                ST_PHRASES,
                APPEND,
                pair("测试甲", "自訂甲"),
                pair("测试乙", "自訂乙")
        );
        OpenccWrapper.OpenccCustomDictSpec tsSpec = spec(
                TS_PHRASES,
                APPEND,
                pair("專屬詞", "定制词")
        );

        try (OpenccWrapper opencc = customWrapper(stSpec, tsSpec)) {
            assertEquals("自訂甲和自訂乙", opencc.convert("测试甲和测试乙", "s2t", false));
            assertEquals("定制词", opencc.convert("專屬詞", "t2s", false));
        }
    }

    @Test
    void emptyOverrideClearsTheSelectedPhraseDictionary() {
        OpenccWrapper.OpenccCustomDictSpec spec = new OpenccWrapper.OpenccCustomDictSpec(
                ST_PHRASES,
                OVERRIDE,
                Collections.emptyList()
        );

        try (OpenccWrapper opencc = customWrapper(spec)) {
            // Without STPhrases, 頭髮 is converted character-by-character to 頭發.
            assertEquals("頭發", opencc.convert("头发", "s2t", false));
        }
    }

    @Test
    void emptySpecListCreatesANormalInstance() {
        try (OpenccWrapper normal = new OpenccWrapper();
             OpenccWrapper custom = new OpenccWrapper(Collections.emptyList())) {
            String input = "头发和鼠标";
            assertEquals(
                    normal.convert(input, "s2t", false),
                    custom.convert(input, "s2t", false)
            );
        }
    }

    @Test
    void customInstanceSupportsParallelSetting() {
        try (OpenccWrapper opencc = customWrapper(spec(
                ST_PHRASES,
                APPEND,
                pair("测试术语", "自訂結果")
        ))) {
            opencc.setParallel(true);
            assertTrue(opencc.isParallel());

            opencc.setParallel(false);
            assertFalse(opencc.isParallel());
        }
    }

    @Test
    void customInstanceCannotBeUsedAfterClose() {
        try (OpenccWrapper opencc = customWrapper(spec(
                ST_PHRASES,
                APPEND,
                pair("测试术语", "自訂結果")
        ))) {
            opencc.close();
            opencc.close(); // close must remain idempotent.

            assertThrows(
                    IllegalStateException.class,
                    () -> opencc.convert("测试术语", "s2t", false)
            );
        }
    }

    @Test
    void constructorRejectsNullSpecList() {
        assertThrows(NullPointerException.class, () -> {
            try (OpenccWrapper ignored = new OpenccWrapper(null)) {
                LOGGER.fine("OpenccWrapper constructor unexpectedly succeeded");
            }
        });
    }

    @Test
    void constructorRejectsNullSpecElement() {
        List<OpenccWrapper.OpenccCustomDictSpec> specs = Collections.singletonList(null);
        assertThrows(NullPointerException.class, () -> {
            try (OpenccWrapper ignored = new OpenccWrapper(specs)) {
                LOGGER.fine("OpenccWrapper constructor unexpectedly succeeded");
            }
        });
    }

    @Test
    void specRejectsNullPairList() {
        assertThrows(
                NullPointerException.class,
                () -> new OpenccWrapper.OpenccCustomDictSpec(ST_PHRASES, APPEND, null)
        );
    }

    @Test
    void constructorRejectsNullPairElement() {
        OpenccWrapper.OpenccCustomDictSpec spec = new OpenccWrapper.OpenccCustomDictSpec(
                ST_PHRASES,
                APPEND,
                Collections.singletonList(null)
        );

        assertThrows(NullPointerException.class, () -> {
            try (OpenccWrapper ignored = new OpenccWrapper(Collections.singletonList(spec))) {
                LOGGER.fine("OpenccWrapper constructor unexpectedly succeeded");
            }
        });
    }

    @Test
    void pairRejectsNullSourceOrTarget() {
        assertThrows(
                NullPointerException.class,
                () -> new OpenccWrapper.OpenccCustomPair(null, "target")
        );
        assertThrows(
                NullPointerException.class,
                () -> new OpenccWrapper.OpenccCustomPair("source", null)
        );
    }

    @Test
    void embeddedNulIsRejectedBeforeCallingRust() {
        OpenccWrapper.OpenccCustomDictSpec sourceNul = spec(
                ST_PHRASES,
                APPEND,
                pair("测\0试", "測試")
        );
        OpenccWrapper.OpenccCustomDictSpec targetNul = spec(
                ST_PHRASES,
                APPEND,
                pair("测试", "測\0試")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (OpenccWrapper ignored = new OpenccWrapper(Collections.singletonList(sourceNul))) {
                        LOGGER.fine("OpenccWrapper constructor unexpectedly succeeded");
                    }
                }
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    try (OpenccWrapper ignored = new OpenccWrapper(Collections.singletonList(targetNul))) {
                        LOGGER.fine("OpenccWrapper constructor unexpectedly succeeded");
                    }
                }
        );
    }

    @Test
    void invalidSlotReturnsTheRustError() {
        OpenccWrapper.OpenccCustomDictSpec spec = spec(
                Integer.MAX_VALUE,
                APPEND,
                pair("测试", "測試")
        );

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> {
                    try (OpenccWrapper ignored = new OpenccWrapper(Collections.singletonList(spec))) {
                        LOGGER.fine("OpenccWrapper constructor unexpectedly succeeded");
                    }
                }
        );

        assertTrue(error.getMessage().toLowerCase().contains("slot"), error.getMessage());
    }

    @Test
    void invalidModeReturnsTheRustError() {
        OpenccWrapper.OpenccCustomDictSpec spec = spec(
                ST_PHRASES,
                Integer.MAX_VALUE,
                pair("测试", "測試")
        );

        RuntimeException error = assertThrows(
                RuntimeException.class,
                () -> {
                    try (OpenccWrapper ignored = new OpenccWrapper(Collections.singletonList(spec))) {
                        LOGGER.fine("OpenccWrapper constructor unexpectedly succeeded");
                    }
                }
        );

        assertTrue(error.getMessage().toLowerCase().contains("mode"), error.getMessage());
    }

    @Test
    void specDefensivelyCopiesAndExposesUnmodifiablePairs() {
        List<OpenccWrapper.OpenccCustomPair> pairs = new ArrayList<>();
        pairs.add(pair("测试", "測試"));

        OpenccWrapper.OpenccCustomDictSpec spec =
                new OpenccWrapper.OpenccCustomDictSpec(ST_PHRASES, APPEND, pairs);
        pairs.clear();

        assertEquals(1, spec.getPairs().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> spec.getPairs().add(pair("新增", "新增"))
        );
    }

    private static OpenccWrapper customWrapper(OpenccWrapper.OpenccCustomDictSpec... specs) {
        return new OpenccWrapper(Arrays.asList(specs));
    }

    private static OpenccWrapper.OpenccCustomDictSpec spec(
            int slot,
            int mode,
            OpenccWrapper.OpenccCustomPair... pairs) {
        return new OpenccWrapper.OpenccCustomDictSpec(slot, mode, Arrays.asList(pairs));
    }

    private static OpenccWrapper.OpenccCustomPair pair(String source, String target) {
        return new OpenccWrapper.OpenccCustomPair(source, target);
    }
}
