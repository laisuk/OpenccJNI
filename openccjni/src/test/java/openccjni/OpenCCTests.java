package openccjni;

import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenCCTests {
    @Test
    void testConvertS2T() {
        try (OpenCC opencc1 = new OpenCC()) {
            String simplified = "简体中文测试";
            String expectedTraditional = "簡體中文測試"; // Ensure your dictionary has these mappings
            String result = opencc1.convert(simplified);

            assertTrue(OpenCC.isSupportedConfig(opencc1.getConfig()));
            assertTrue(OpenCC.isSupportedConfig(opencc1.getConfigId().toCanonicalName()));
            assertEquals(expectedTraditional, result);
            assertNotNull(result);
            assertTrue(result.contains("簡"), "Should contain converted character");
        }
    }

    @Test
    void testConvertS2TWP() {
        String simplified = "欧洲古国意大利";
        String expectedTraditional = "歐洲古國義大利"; // Ensure your dictionary has these mappings
        String result = OpenCC.convert(simplified, "s2twp");

        assertEquals(expectedTraditional, result);
        assertNotNull(result);
        assertTrue(result.contains("義"), "Should contain converted character");
    }

    @Test
    void testPunctuationConversionS2T() {
        try (OpenCC opencc2 = new OpenCC("s2t")) {
            String input = "“你好”";
            opencc2.setConfig("s2tw");
            String result1 = opencc2.convert(input, true);
            assertEquals("「你好」", result1);
            String result2 = OpenCC.convert(input, "s2t", true);
            assertEquals("「你好」", result2);
        }
    }

    @Test
    void testNullInputStaticConvert() {
        OpenCC.setLastError(null);
        assertNull(OpenCC.convert(null, "s2t"));
        assertEquals("Input is null", OpenCC.getLastError());

        OpenCC.setLastError(null);
        assertNull(OpenCC.convert(null, "s2t", true));
        assertEquals("Input is null", OpenCC.getLastError());
    }

    @Test
    void testNullInputInstanceConvert() {
        try (OpenCC opencc1 = new OpenCC()) {
            // --- convert(String) ---
            OpenCC.setLastError(null);

            String r1 = opencc1.convert(null);

            assertNull(r1, "Null input should return null");
            assertEquals("Input is null", OpenCC.getLastError());

            // --- convert(String, boolean) ---
            OpenCC.setLastError(null);

            String r2 = opencc1.convert(null, true);

            assertNull(r2, "Null input should return null");
            assertEquals("Input is null", OpenCC.getLastError());
        }
    }

    @Test
    void testZhoCheckTraditional() {
        String text = "繁體中文";
        int result = OpenCC.zhoCheck(text);
        assertEquals(1, result); // 1 = traditional
    }

    @Test
    void testZhoCheckSimplified() {
        String text = "简体中文";
        int result = OpenCC.zhoCheck(text);
        assertEquals(2, result); // 2 = simplified
    }

    @Test
    void testZhoCheckUnknown() {
        String text = "hello world!";
        int result = OpenCC.zhoCheck(text);
        assertEquals(0, result); // not Chinese
    }

    @Test
    public void testS2T_100kCharacters() {
        try (OpenCC opencc1 = new OpenCC()) {
            // Generate 100,000 characters from a repeated simplified phrase
            String base = "汉字转换";
            StringBuilder inputBuilder = new StringBuilder(100_000);
            while (inputBuilder.length() < 100_000) {
                inputBuilder.append(base);
            }
            String input = inputBuilder.toString();

            // Time the conversion
            long start = System.nanoTime();
            String config = opencc1.getConfig();
            if (!Objects.equals(config, "s2t")) {
                opencc1.setConfig("s2t");
            }
            String output = opencc1.convert(input); // simplified to traditional
            long durationMs = (System.nanoTime() - start) / 1_000_000;

            // Assertions
            assertNotNull(output);
            assertEquals(input.length(), output.length()); // rough check, assuming 1:1 mapping
            System.out.println("s2t() conversion of 100K chars completed in " + durationMs + " ms");

        }
    }

    @Test
    void testConfigFallback() {
        try (OpenCC bad = new OpenCC("invalid_config")) {
            assertNotNull(OpenCC.getLastError());
            assertEquals("s2t", bad.getConfig());
            assertEquals("測試", bad.convert("测试"));
        }
    }

    @Test
    void testValidConfigClearsLastErrorAfterInvalidConstructorInput() {
        OpenCC.setLastError(null);

        try (OpenCC bad = new OpenCC("invalid_config")) {
            assertEquals("Invalid config: invalid_config", OpenCC.getLastError());
        }

        try (OpenCC good = OpenCC.fromConfig(OpenccConfig.S2TW)) {
            assertEquals("s2tw", good.getConfig());
            assertEquals("", OpenCC.getLastError());
        }
    }

    @Test
    void testSetConfigClearsLastErrorAfterInvalidInput() {
        try (OpenCC cc = new OpenCC()) {

            cc.setConfig("invalid_config");
            assertEquals("Invalid config: invalid_config", OpenCC.getLastError());

            cc.setConfig(OpenccConfig.TW2S);
            assertEquals("tw2s", cc.getConfig());
            assertEquals("", OpenCC.getLastError());
        }
    }

    @Test
    void testNativeNoErrorSentinelIsNormalizedToEmptyString() {
        OpenCC.setLastError(null);

        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals("", w.getLastError());
        }
    }

    @Test
    void testConfigEnum() {
        OpenccConfig configEnum = OpenccConfig.tryParse("s2twp");
        String ConfigStr = configEnum.toCanonicalName();
        assertEquals("s2twp", ConfigStr);
    }

    @Test
    void testConfigEnumRoundTrip() {
        // ✅ Case-insensitive matching
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("s2twp"));
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("S2Twp"));
        assertEquals(OpenccConfig.S2TWP, OpenccConfig.tryParse("S2TWP"));
        // ✅ Round-trip consistency
        for (OpenccConfig cfg : OpenccConfig.values()) {
            assertEquals(cfg, OpenccConfig.tryParse(cfg.toCanonicalName()));
            assertEquals(cfg.toCanonicalName(), cfg.toCanonicalName().toLowerCase()); // ensure lowercase form
        }
    }

    @Test
    void testInvalidConfigTryParseReturnsNull() {
        // ✅ Null input → tolerant: returns null
        assertNull(OpenccConfig.tryParse(null));

        // ✅ Empty / whitespace → tolerant: returns null
        assertNull(OpenccConfig.tryParse(""));
        assertNull(OpenccConfig.tryParse("   "));

        // ✅ Unknown config → tolerant: returns null
        assertNull(OpenccConfig.tryParse("invalid"));
        assertNull(OpenccConfig.tryParse("t2xyz"));
    }

    @Test
    void testInvalidConfigIsRejected() {
        // ✅ Null / empty / whitespace
        assertFalse(OpenccConfig.isValidConfig(null));
        assertFalse(OpenccConfig.isValidConfig(""));
        assertFalse(OpenccConfig.isValidConfig("   "));

        // ✅ Unknown config
        assertFalse(OpenccConfig.isValidConfig("invalid"));
        assertFalse(OpenccConfig.isValidConfig("t2xyz"));

        // ✅ tryParse returns null for invalid inputs
        assertNull(OpenccConfig.tryParse(null));
        assertNull(OpenccConfig.tryParse(""));
        assertNull(OpenccConfig.tryParse("invalid"));
        assertNull(OpenccConfig.tryParse("t2xyz"));
    }

    @Test
    void testTryParseRoundTrip() {
        for (OpenccConfig c : OpenccConfig.values()) {
            assertEquals(c, OpenccConfig.tryParse(c.toCanonicalName()));
            assertEquals(c, OpenccConfig.tryParse(c.name())); // enum-style
        }
    }

    @Test
    void testAbiNumberIsPositive() {
        int abi = OpenccWrapper.getAbiNumber();
        assertTrue(abi > 0, "ABI number should be > 0, got: " + abi);
    }

    @Test
    void testVersionStringIsNotBlank() {
        String ver = OpenccWrapper.getVersionString();
        assertNotNull(ver, "Version string should not be null");
        assertFalse(ver.trim().isEmpty(), "Version string should not be blank");
    }

    @Test
    void testVersionStringLooksLikeSemverPrefix() {
        // Allow "0.8.4", "0.8.4+meta", "0.8.4-rc1", etc.
        String ver = OpenccWrapper.getVersionString();
        assertNotNull(ver);
        assertTrue(ver.matches("^\\d+\\.\\d+\\.\\d+.*$"),
                "Version string should look like semver (x.y.z...), got: " + ver);
    }

    @Test
    void testCanCreateWrapperAndConvertBasic() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            String out = w.convert("汉字转换测试", "s2t", false);
            assertNotNull(out);
            assertFalse(out.isEmpty());
            // Not asserting exact output here to avoid dict-version sensitivity.
        }
    }


    // ------------------------------------------------------------------------
    // Compatibility normalization / DeTofu JNI wrapper tests
    // ------------------------------------------------------------------------

    @Test
    void testNormalizeCompat() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals("金庸", w.normalizeCompat("金庸"));
        }
    }

    @Test
    void testNormalizeCompatNonBmpCompatibilityIdeographs() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals("鼖鼻𪘀", w.normalizeCompat("鼖鼻𪘀"));
        }
    }

    @Test
    void testNormalizeCompatExtended() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals(
                    "聽聽奇美玉石瓶器音",
                    w.normalizeCompatExtended("聼聼竒羙⽟䂖甁噐⾳")
            );
        }
    }

    @Test
    void testNormalizeCompatExtendedIncludesCjkCompatNormalization() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals(
                    "天龍八部書裡的聽眾",
                    w.normalizeCompatExtended("天龍八部書裡的聼眾")
            );
        }
    }

    @Test
    void testDeTofuBuiltin() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals("骖騑", w.deTofu("骖𬴂", 0));
        }
    }

    @Test
    void testDeTofuPreservesUnmappedCharacter() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals("𱁬", w.deTofu("𱁬", 0));
        }
    }

    @Test
    void testNormalizationAndDeTofuEmptyInput() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertEquals("", w.normalizeCompat(""));
            assertEquals("", w.normalizeCompatExtended(""));
            assertEquals("", w.deTofu("", 0));
        }
    }

    @Test
    void testNormalizationAndDeTofuRejectNullInput() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertThrows(NullPointerException.class, () -> w.normalizeCompat(null));
            assertThrows(NullPointerException.class, () -> w.normalizeCompatExtended(null));
            assertThrows(NullPointerException.class, () -> w.deTofu(null, 0));
        }
    }

    @Test
    void testDeTofuRejectsInvalidLevel() {
        try (OpenccWrapper w = new OpenccWrapper()) {
            assertThrows(IllegalArgumentException.class, () -> w.deTofu("𬴂", -1));
            assertThrows(IllegalArgumentException.class, () -> w.deTofu("𬴂", 8));
        }
    }

    @Test
    void testNormalizationAndDeTofuRejectClosedWrapper() {
        OpenccWrapper w = new OpenccWrapper();
        w.close();

        assertThrows(IllegalStateException.class, () -> w.normalizeCompat("金"));
        assertThrows(IllegalStateException.class, () -> w.normalizeCompatExtended("聼"));
        assertThrows(IllegalStateException.class, () -> w.deTofu("𬴂", 0));
    }

    // ------------------------------------------------------------------------
// High-level normalization / DeTofu API tests
// ------------------------------------------------------------------------

    @Test
    void testOpenCCNormalizeCompat() {
        try (OpenCC cc = new OpenCC()) {
            assertEquals("金庸", cc.normalizeCompat("金庸"));
        }
    }

    @Test
    void testOpenCCNormalizeCompatNonBmp() {
        try (OpenCC cc = new OpenCC()) {
            assertEquals(
                    "鼖鼻𪘀",
                    cc.normalizeCompat("鼖鼻𪘀")
            );
        }
    }

    @Test
    void testOpenCCNormalizeCompatExtended() {
        try (OpenCC cc = new OpenCC()) {
            assertEquals(
                    "聽聽奇美玉石瓶器音",
                    cc.normalizeCompatExtended("聼聼竒羙⽟䂖甁噐⾳")
            );
        }
    }

    @Test
    void testOpenCCNormalizeCompatExtendedIncludesCjkCompat() {
        try (OpenCC cc = new OpenCC()) {
            assertEquals(
                    "天龍八部書裡的聽眾",
                    cc.normalizeCompatExtended("天龍八部書裡的聼眾")
            );
        }
    }

    @Test
    void testOpenCCDeTofu() {
        try (OpenCC cc = new OpenCC()) {
            assertEquals(
                    "骖騑",
                    cc.deTofu("骖𬴂", DeTofuLevel.EXT_B)
            );
        }
    }

    @Test
    void testOpenCCDeTofuPreservesUnmappedCharacter() {
        try (OpenCC cc = new OpenCC()) {
            assertEquals(
                    "𱁬",
                    cc.deTofu("𱁬", DeTofuLevel.EXT_B)
            );
        }
    }

    @Test
    void testOpenCCDeTofuRejectsNullLevel() {
        try (OpenCC cc = new OpenCC()) {
            assertThrows(
                    NullPointerException.class,
                    () -> cc.deTofu("𬴂", null)
            );
        }
    }

    @Test
    void testOpenCCNormalizationAndDeTofuEmptyInput() {
        try (OpenCC cc = new OpenCC()) {
            assertEquals("", cc.normalizeCompat(""));
            assertEquals("", cc.normalizeCompatExtended(""));
            assertEquals("", cc.deTofu("", DeTofuLevel.EXT_B));
        }
    }

    @Test
    void testOpenCCNormalizationAndDeTofuRejectNullInput() {
        try (OpenCC cc = new OpenCC()) {
            assertThrows(
                    NullPointerException.class,
                    () -> cc.normalizeCompat(null)
            );
            assertThrows(
                    NullPointerException.class,
                    () -> cc.normalizeCompatExtended(null)
            );
            assertThrows(
                    NullPointerException.class,
                    () -> cc.deTofu(null, DeTofuLevel.EXT_B)
            );
        }
    }

    @Test
    void testOpenCCNormalizationAndDeTofuRejectClosedInstance() {
        OpenCC cc = new OpenCC();
        cc.close();

        assertThrows(
                IllegalStateException.class,
                () -> cc.normalizeCompat("金")
        );
        assertThrows(
                IllegalStateException.class,
                () -> cc.normalizeCompatExtended("聼")
        );
        assertThrows(
                IllegalStateException.class,
                () -> cc.deTofu("𬴂", DeTofuLevel.EXT_B)
        );
    }

    @Test
    void testOpenCCNormalizeDeTofuAndConvertComposition() {
        try (OpenCC cc = new OpenCC(OpenccConfig.T2S)) {
            String normalized =
                    cc.normalizeCompatExtended("天龍八部書裡的聼眾");

            assertEquals(
                    "天龍八部書裡的聽眾",
                    normalized
            );

            assertEquals(
                    "天龙八部书里的听众",
                    cc.convert(normalized)
            );

            assertEquals(
                    "俨骖騑于上路",
                    cc.deTofu(
                            cc.convert("儼驂騑於上路"),
                            DeTofuLevel.EXT_B
                    )
            );
        }
    }

    @Test
    void testSealConfigsSupported() {
        assertEquals(OpenccConfig.S2SEAL, OpenccConfig.tryParse("s2seal"));
        assertEquals(OpenccConfig.T2SEAL, OpenccConfig.tryParse("T2SEAL"));
        assertEquals(OpenccConfig.SEAL2S, OpenccConfig.tryParse("seal2s"));
        assertEquals(OpenccConfig.SEAL2T, OpenccConfig.tryParse("SEAL2T"));

        assertTrue(OpenccConfig.isValidConfig("s2seal"));
        assertTrue(OpenccConfig.isValidConfig("t2seal"));
        assertTrue(OpenccConfig.isValidConfig("seal2s"));
        assertTrue(OpenccConfig.isValidConfig("seal2t"));
    }

}


