package openccjni;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenCCTests {
    static OpenCC opencc1;
    static OpenCC opencc2;

    @BeforeAll
    static void init() {
        opencc1 = new OpenCC();
        opencc2 = new OpenCC("s2t");

    }

    @Test
    void testConvertS2T() {
        String simplified = "简体中文测试";
        String expectedTraditional = "簡體中文測試"; // Ensure your dictionary has these mappings
        String result = opencc1.convert(simplified);

        assertEquals(expectedTraditional, result);
        assertTrue(result.contains("簡"), "Should contain converted character");
    }

    @Test
    void testConvertS2TWP() {
        String simplified = "欧洲古国意大利";
        String expectedTraditional = "歐洲古國義大利"; // Ensure your dictionary has these mappings
        String result = OpenCC.convert(simplified, "s2twp");

        assertEquals(expectedTraditional, result);
        assertTrue(result.contains("義"), "Should contain converted character");
    }

    @Test
    void testPunctuationConversionS2T() {
        String input = "“你好”";
        opencc2.setConfig("s2tw");
        String result1 = opencc2.convert(input, true);
        assertEquals("「你好」", result1);
        String result2 = OpenCC.convert(input, "s2t", true);
        assertEquals("「你好」", result2);
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

    @Test
    void testConfigFallback() {
        OpenCC bad = new OpenCC("invalid_config");
        assertEquals("s2t", bad.getConfig());
        assertEquals("測試", bad.convert("测试"));
        assertNotNull(OpenCC.getLastError());
        System.out.println("Last Error: " + OpenCC.getLastError());
    }
}
