package openccjni;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficeHelperTest {
    @Test
    void convertsInlineStringsInXlsxWorksheetWithoutTouchingFormulas() throws IOException {
        byte[] workbook = createInlineStringWorkbook();

        OfficeHelper.MemoryResult result = OfficeHelper.convert(
                workbook,
                "xlsx",
                new OpenCC("s2t"),
                false,
                false
        );

        assertTrue(result.success, result.message);
        assertNotNull(result.data);

        String sheetXml = unzipEntry(result.data, "xl/worksheets/sheet1.xml");
        assertNotNull(sheetXml);
        assertTrue(sheetXml.contains("<t>簡體中文</t>"), sheetXml);
        assertTrue(sheetXml.contains("<f>IF(A1=\"简体\",1,0)</f>"), sheetXml);
        assertFalse(sheetXml.contains("<t>简体中文</t>"), sheetXml);
    }

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

    @Test
    void officeFormatsIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () ->
                OfficeHelper.OFFICE_FORMATS.set(0, "changed"));
    }

    @Test
    void resultMessageMustNotBeNull() {
        assertThrows(NullPointerException.class, () ->
                new OfficeHelper.FileResult(true, null));
    }

    @Test
    void memoryResultDefensivelyCopiesInputData() {
        byte[] input = {1, 2, 3};
        OfficeHelper.MemoryResult result = new OfficeHelper.MemoryResult(true, "ok", input);

        input[0] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, result.data);
    }

    private static byte[] createInlineStringWorkbook() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                        "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                        "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                        "</Types>");
        entries.put("_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                        "</Relationships>");
        entries.put("xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                        "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                        "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                        "</workbook>");
        entries.put("xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                        "</Relationships>");
        entries.put("xl/worksheets/sheet1.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                        "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                        "<sheetData><row r=\"1\">" +
                        "<c r=\"A1\" t=\"inlineStr\"><is><t>简体中文</t></is></c>" +
                        "<c r=\"B1\"><f>IF(A1=\"简体\",1,0)</f></c>" +
                        "</row></sheetData>" +
                        "</worksheet>");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String unzipEntry(byte[] zipBytes, String name) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    return new String(output.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
