package openccjnicli;

import openccjni.DictSlot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliUtilsTest {

    @Test
    void compatibilityAliasNamesAreRejectedAsCliInputs() {
        assertThrows(IllegalArgumentException.class, () -> CliUtils.parseDictSlot("JPVariants"));
        assertThrows(IllegalArgumentException.class, () -> CliUtils.parseDictSlot("JPVariantsRev"));
    }

    @Test
    void availableSlotsExcludeCompatibilityAliasNames() {
        String availableSlots = CliUtils.availableDictSlots();

        assertFalse(availableSlots.contains("JPVariants"), availableSlots);
        assertFalse(availableSlots.contains("JPVariantsRev"), availableSlots);
    }

    @Test
    void normalSlotsRemainAcceptedAsCliInputs() {
        assertEquals(DictSlot.STCharacters, CliUtils.parseDictSlot("STCharacters"));
        assertEquals(DictSlot.JPSCharacters, CliUtils.parseDictSlot("JPSCharacters"));
    }

    @Test
    void dictionarySlotFilteringDoesNotUseReflectionApis() throws IOException {
        String classFile = classFileContents(CliUtils.class);

        assertFalse(classFile.contains("getField"), "CliUtils must not call Class.getField");
        assertFalse(classFile.contains("isAnnotationPresent"),
                "CliUtils must not inspect annotations at runtime");
        assertFalse(classFile.contains("java/lang/reflect"),
                "CliUtils must not use reflection APIs for slot filtering");
    }

    private static String classFileContents(Class<?> type) throws IOException {
        String resourceName = type.getSimpleName() + ".class";
        InputStream input = type.getResourceAsStream(resourceName);
        assertTrue(input != null, "Could not load " + resourceName);

        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
