package openccjni;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class OfficePipelineTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @CsvSource({"docx,word/document.xml", "xlsx,xl/sharedStrings.xml", "pptx,ppt/slides/slide1.xml",
            "odt,content.xml", "ods,content.xml", "odp,content.xml", "epub,OEBPS/chapter.xhtml"})
    void memoryAndFilePipelinesPreserveOtherEntries(String format, String part) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("unrelated.xml", "简体中文");
        entries.put(part, "<text>简体中文</text>");
        if ("epub".equals(format)) entries.put("mimetype", "application/epub+zip");
        byte[] archive = archive(entries);
        TextConverter callback = text -> text.replace("简体中文", "PIPELINE_OK");
        OfficeHelper.MemoryResult memory = OfficeHelper.convert(archive, format.toUpperCase(Locale.ROOT), callback, false);
        assertTrue(memory.success, memory.message);
        assertEquals("<text>PIPELINE_OK</text>", entry(memory.data, part));
        assertEquals("简体中文", entry(memory.data, "unrelated.xml"));

        Path input = tempDir.resolve("input." + format);
        Path output = tempDir.resolve("nested/output." + format);
        Files.write(input, archive);
        OfficeHelper.FileResult file = OfficeHelper.convert(input.toFile(), output.toFile(), format, callback, false);
        assertTrue(file.success, file.message);
        assertEquals(entry(memory.data, part), entry(Files.readAllBytes(output), part));
        assertEquals("简体中文", entry(Files.readAllBytes(output), "unrelated.xml"));
        if ("epub".equals(format)) {
            assertEpubFirstStored(memory.data);
            assertEpubFirstStored(Files.readAllBytes(output));
        }
    }

    @Test
    void convertsInlineStringsOnlyAndComposesCallback() throws Exception {
        String xml = "<worksheet><c t='inlineStr'><is><t>简体中文</t><t>简体中文</t></is></c>"
                + "<c><f>简体中文</f><v>简体中文</v></c></worksheet>";
        byte[] archive = archive(Collections.singletonMap("xl/worksheets/sheet1.xml", xml));
        OfficeHelper.MemoryResult result = OfficeHelper.convert(archive, "xlsx", text -> "[" + text.replace("简体中文", "converted") + "]", false);
        assertTrue(result.success, result.message);
        assertEquals(xml.replace("<t>简体中文</t>", "<t>[converted]</t>"), entry(result.data, "xl/worksheets/sheet1.xml"));
    }

    @Test
    void failedCallbacksPreserveExistingOutputAndCleanTemporaryFiles() throws Exception {
        Path input = tempDir.resolve("source.docx");
        Path output = tempDir.resolve("destination.docx");
        Files.write(input, archive(Collections.singletonMap("word/document.xml", "简体")));
        byte[] sentinel = "sentinel".getBytes(StandardCharsets.UTF_8);
        Files.write(output, sentinel);
        for (TextConverter callback : Arrays.<TextConverter>asList(text -> null, text -> { throw new IllegalStateException("callback failed"); })) {
            OfficeHelper.FileResult result = OfficeHelper.convert(input.toFile(), output.toFile(), "docx", callback, false);
            assertFalse(result.success);
            assertArrayEquals(sentinel, Files.readAllBytes(output));
            OfficeHelper.MemoryResult memory = OfficeHelper.convert(Files.readAllBytes(input), "docx", callback, false);
            assertFalse(memory.success);
            assertNull(memory.data);
        }
        try (java.util.stream.Stream<Path> files = Files.list(tempDir)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void inPlaceFileConversionClosesSourceBeforePublication() throws Exception {
        Path input = tempDir.resolve("inplace.docx");
        Files.write(input, archive(Collections.singletonMap("word/document.xml", "简体")));
        OfficeHelper.FileResult result = OfficeHelper.convert(input.toFile(), input.toFile(), "docx",
                text -> text.replace("简体", "簡體"), false);
        assertTrue(result.success, result.message);
        assertEquals("簡體", entry(Files.readAllBytes(input), "word/document.xml"));
    }

    @Test
    void rejectsUnsafeEntriesMissingTargetsAndMissingEpubMimetype() throws Exception {
        Map<String, String> unsafe = new LinkedHashMap<>();
        unsafe.put("word/document.xml", "text");
        unsafe.put("../escape.xml", "text");
        OfficeHelper.MemoryResult result = OfficeHelper.convert(archive(unsafe), "docx", text -> text, false);
        assertFalse(result.success);
        assertTrue(result.message.contains("Unsafe ZIP entry path"), result.message);
        assertFalse(OfficeHelper.convert(archive(Collections.singletonMap("other.xml", "text")), "docx", text -> text, false).success);
        assertFalse(OfficeHelper.convert(archive(Collections.singletonMap("chapter.xhtml", "text")), "epub", text -> text, false).success);
        assertFalse(OfficeHelper.convert(new byte[]{1, 2, 3}, "docx", text -> text, false).success);
    }

    @Test
    void validatesInputsAndCallbackContract() throws Exception {
        byte[] archive = archive(Collections.singletonMap("word/document.xml", "text"));
        assertFalse(OfficeHelper.convert(archive, "docx", (TextConverter) null, false).success);
        assertFalse(OfficeHelper.convert(archive, null, text -> text, false).success);
        assertFalse(OfficeHelper.convert(new byte[0], "docx", text -> text, false).success);
        assertFalse(OfficeHelper.convert(null, tempDir.resolve("output.docx").toFile(), "docx", text -> text, false).success);
        OfficeHelper.MemoryResult nullReturn = OfficeHelper.convert(archive, "docx", text -> null, false);
        assertTrue(nullReturn.message.contains("Office text converter returned null"));
    }

    @Test
    void convenienceOverloadBorrowsNativeConverter() throws Exception {
        byte[] archive = archive(Collections.singletonMap("word/document.xml", "简体中文"));
        try (OpenCC owner = new OpenCC("s2t")) {
            OfficeHelper.MemoryResult result = OfficeHelper.convert(archive, "docx", owner, false, false);
            assertTrue(result.success, result.message);
            assertEquals("簡體中文", entry(result.data, "word/document.xml"));
            assertEquals("簡體中文", owner.convert("简体中文"));
        }
    }

    private static byte[] archive(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String entry(byte[] archive, String name) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = zip.read(buffer)) != -1) bytes.write(buffer, 0, count);
                    return bytes.toString("UTF-8");
                }
            }
        }
        return null;
    }

    private static void assertEpubFirstStored(byte[] archive) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry first = zip.getNextEntry();
            assertNotNull(first);
            assertEquals("mimetype", first.getName());
            assertEquals(ZipEntry.STORED, first.getMethod());
        }
    }
}