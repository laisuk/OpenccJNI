package openccjni;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficeHelperTest {

    @Test
    void convertRejectsNullOutputFile() {
        OfficeHelper.FileResult result = OfficeHelper.convert(
                new File("unused.docx"),
                null,
                "docx",
                null,
                false,
                false
        );

        assertFalse(result.success);
        assertTrue(result.message.contains("Output file must not be null"));
    }
}
