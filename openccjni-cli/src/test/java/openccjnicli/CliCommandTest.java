package openccjnicli;

import openccjni.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class CliCommandTest {
    @TempDir Path tempDir;

    @Test
    void convertMissingAndInvalidConfigReturnUsage() throws Exception {
        Result missing = execute("", "convert");
        assertEquals(2, missing.code);
        assertTrue(missing.err.contains("Missing required option"));
        Result invalid = execute("", "convert", "-c", "not-a-config");
        assertEquals(2, invalid.code);
        assertTrue(invalid.err.contains("Supported configs:"));
        assertTrue(invalid.err.contains("hk2tp"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"convert", "office", "pdf"})
    void helpShowsSharedOptionsAndNativeSlots(String command) throws Exception {
        Result help = execute("", command, "--help");
        assertEquals(0, help.code);
        for (String option : Arrays.asList("--norm-compat", "--norm-compat-extended", "--detofu", "t2hkp", "hk2tp", "STCharacters")) {
            assertTrue(help.out.contains(option), help.out);
        }
        assertFalse(help.out.contains("--detofu-file"));
        assertFalse(help.out.contains("JPVariants"));
    }

    @Test
    void normAndExtendedNormRunBeforeConversion() throws Exception {
        assertEquals("金庸", convert("金庸", "-n"));
        assertEquals("鼖鼻𪚏", convert("鼖鼻𪘀", "--norm-compat"));
        assertEquals("天龙八部书里的听众", convert("天龍八部書裡的聼眾", "-E"));
        assertEquals("天龙八部书里的听众", convert("天龍八部書裡的聼眾", "-n", "-E"));
        assertEquals("俨骖騑于上路", convert("儼驂騑於上路", "--detofu", "all"));
        assertEquals("天龙八部书里的听众俨骖騑于上路",
                convert("天龍八部書裡的聼眾儼驂騑於上路", "-E", "--detofu", "ext-b"));
        assertEquals("", convert("", "-E", "--detofu", "all"));
    }

    @Test
    void deTofuThresholdsAliasesAndUnmappedCharacters() throws Exception {
        assertEquals("騑𱁬", convert("𬴂𱁬", "--detofu", "all"));
        assertEquals("𬴂", convert("𬴂", "--detofu", "ext-i"));
        for (DeTofuLevel level : DeTofuLevel.values()) {
            String suffix = level.name().substring(4).toLowerCase(Locale.ROOT);
            assertEquals(level, CliUtils.parseDeTofuLevel("ext-" + suffix));
            assertEquals(level, CliUtils.parseDeTofuLevel("Ext" + suffix));
            assertEquals(level, CliUtils.parseDeTofuLevel(" " + suffix.toUpperCase(Locale.ROOT) + " "));
        }
        assertEquals(DeTofuLevel.EXT_B, CliUtils.parseDeTofuLevel("ALL"));
        assertNull(CliUtils.parseDeTofuLevel(" "));
    }

    @Test
    void invalidOptionsAndFailuresDoNotOverwriteTextOutput() throws Exception {
        Path input = tempDir.resolve("input.txt");
        Path output = tempDir.resolve("output.txt");
        Files.write(input, "金".getBytes(StandardCharsets.UTF_8));
        Files.write(output, "sentinel".getBytes(StandardCharsets.UTF_8));
        for (String[] options : new String[][]{{"--detofu", "invalid"}, {"--out-enc", "invalid-encoding"}}) {
            List<String> args = new ArrayList<>(Arrays.asList("convert", "-c", "t2s", "-i", input.toString(), "-o", output.toString()));
            args.addAll(Arrays.asList(options));
            assertEquals(CommandLine.ExitCode.SOFTWARE, execute("", args.toArray(new String[0])).code);
            assertEquals("sentinel", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
        }
        assertEquals(2, execute("", "convert", "-c", "t2s", "--detofu-file", "unsupported.txt").code);
        assertEquals(CommandLine.ExitCode.SOFTWARE, execute("", "convert", "-c", "t2s", "-i", tempDir.resolve("missing.txt").toString()).code);
    }

    @Test
    void fileAndRedirectedStreamEncodingMatchReference() throws Exception {
        Path input = tempDir.resolve("big5.txt");
        Path output = tempDir.resolve("gbk.txt");
        Files.write(input, "簡體中文".getBytes(Charset.forName("Big5")));
        assertEquals(0, execute("", "convert", "-c", " T2S ", "-i", input.toString(), "-o", output.toString(),
                "--in-enc", "Big5", "--out-enc", "GBK").code);
        assertEquals("简体中文", new String(Files.readAllBytes(output), Charset.forName("GBK")));
        Result piped = execute("金庸", "convert", "-c", "t2s", "-n", "--con-enc", "UTF8");
        assertEquals(0, piped.code);
        assertEquals("金庸", piped.out);
        // Stable CLI uses --con-enc for stdout even when redirected.
        assertTrue(piped.err.contains("Output (Charset: UTF-8)"));
    }

    @Test
    void customDictionaryOptionOrderMatchesReference() throws Exception {
        Path input = tempDir.resolve("custom-input.txt");
        Path first = tempDir.resolve("first.txt");
        Path second = tempDir.resolve("second.txt");
        Path output = tempDir.resolve("custom-output.txt");
        Files.write(input, "甲乙".getBytes(StandardCharsets.UTF_8));
        Files.write(first, "甲\t一\n乙\t二\n".getBytes(StandardCharsets.UTF_8));
        Files.write(second, "甲\t三\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, execute("", "convert", "-c", "s2t", "-i", input.toString(), "-o", output.toString(),
                "-D", "STPhrases:override:" + first, "-D", "STPhrases:append:" + second).code);
        assertEquals("三二", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
    }

    @Test
    void officeUsesPipelineProtectsFontsAndKeepsFormulas() throws Exception {
        Path input = tempDir.resolve("book.xlsx");
        String source = "天龍八部書裡的聼眾儼驂騑於上路";
        writeZip(input, "xl/worksheets/sheet1.xml",
                "<worksheet><c t=\"inlineStr\"><is><t>" + source + "</t></is></c><c><f>" + source + "</f></c></worksheet>");
        Result converted = execute("", "office", "-i", input.toString(), "-c", "t2s", "-n", "-E", "--detofu", "all");
        assertEquals(0, converted.code, converted.err);
        String xml = readZip(tempDir.resolve("book_converted.xlsx"), "xl/worksheets/sheet1.xml");
        assertTrue(xml.contains("<t>天龙八部书里的听众俨骖騑于上路</t>"), xml);
        assertTrue(xml.contains("<f>" + source + "</f>"), xml);

        Path docx = tempDir.resolve("fonts.docx");
        writeZip(docx, "word/document.xml", "<w:rFonts w:eastAsia=\"聼金\"/><w:t>聼金</w:t>");
        Path out = tempDir.resolve("fonts-output");
        assertEquals(0, execute("", "office", "-i", docx.toString(), "-o", out.toString(), "-c", "t2s", "-E", "-k").code);
        String fontXml = readZip(tempDir.resolve("fonts-output.docx"), "word/document.xml");
        assertTrue(fontXml.contains("w:eastAsia=\"聼金\""), fontXml);
        assertTrue(fontXml.contains("<w:t>听金</w:t>"), fontXml);
    }

    @Test
    void officeErrorsReturnExitCodesWithoutTerminatingJvm() throws Exception {
        Path input = tempDir.resolve("bad.docx");
        Path output = tempDir.resolve("existing.docx");
        Files.write(input, "not a zip".getBytes(StandardCharsets.UTF_8));
        Files.write(output, "sentinel".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, execute("", "office", "-i", input.toString(), "-o", output.toString(), "-c", "t2s").code);
        assertEquals("sentinel", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
        assertEquals(1, execute("", "office", "-i", input.toString(), "-c", "t2s", "-f", "bad").code);
        assertEquals(1, execute("", "office", "-i", input.toString(), "-c", "t2s", "--detofu", "bad").code);
    }

    @Test
    void pdfExtractionAndSharedPipelineFlags() throws Exception {
        Path input = tempDir.resolve("sample.pdf");
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("PDF pipeline sample");
                stream.endText();
            }
            pdf.save(input.toFile());
        }
        assertEquals(2, execute("", "pdf", "-i", input.toString(), "-c", "invalid").code);
        Result extracted = execute("", "pdf", "-i", input.toString(), "-e", "-n", "-E", "--detofu", "invalid");
        assertEquals(0, extracted.code, extracted.err);
        assertTrue(extracted.err.contains("have no effect in extract-only mode"));
        assertTrue(new String(Files.readAllBytes(tempDir.resolve("sample_extracted.txt")), StandardCharsets.UTF_8).contains("PDF pipeline sample"));
        assertEquals(0, execute("", "pdf", "-i", input.toString(), "-c", "t2s", "-E", "--detofu", "all").code);
        assertTrue(Files.size(tempDir.resolve("sample_converted.txt")) > 0);
    }

    @Test
    void callbackBorrowsOpenCCAndRejectsUseAfterOwnerCloses() {
        OpenCC owner = new OpenCC("t2s");
        try {
            assertThrows(IllegalArgumentException.class, () -> CliUtils.createTextConverter(null, false, false, false, null));
            final openccjni.TextConverter callback = CliUtils.createTextConverter(owner, false, true, true, "all");
            assertEquals("听金", callback.convert("聼金"));
            owner.close();
            assertThrows(IllegalStateException.class, () -> callback.convert("聼"));
        } finally {
            owner.close();
        }
    }

    @Test
    void directHongKongPhraseConfigsMatchStableReference() throws Exception {
        Path input = tempDir.resolve("traditional.txt");
        Path hk = tempDir.resolve("hk.txt");
        Path roundtrip = tempDir.resolve("roundtrip.txt");
        Files.write(input, "光標".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, execute("", "convert", "-c", "t2hkp", "-i", input.toString(), "-o", hk.toString()).code);
        assertEquals("游標", new String(Files.readAllBytes(hk), StandardCharsets.UTF_8));
        assertEquals(0, execute("", "convert", "-c", "hk2tp", "-i", hk.toString(), "-o", roundtrip.toString()).code);
        assertEquals("光標", new String(Files.readAllBytes(roundtrip), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-n", "-E", "--detofu"})
    void officeIndividualPipelineOptions(String option) throws Exception {
        String inputText = "-n".equals(option) ? "金庸" : "-E".equals(option) ? "聼金" : "儼驂騑於上路";
        String expected = "-n".equals(option) ? "金庸" : "-E".equals(option) ? "听金" : "俨骖騑于上路";
        Path input = tempDir.resolve("individual.docx");
        Path output = tempDir.resolve("individual-output.docx");
        writeZip(input, "word/document.xml", "<w:t>" + inputText + "</w:t>");
        List<String> args = new ArrayList<>(Arrays.asList("office", "-i", input.toString(),
                "-o", output.toString(), "-c", "t2s", option));
        if ("--detofu".equals(option)) args.add("all");
        Result result = execute("", args.toArray(new String[0]));
        assertEquals(0, result.code, result.err);
        assertEquals("<w:t>" + expected + "</w:t>", readZip(output, "word/document.xml"));
    }

    private String convert(String text, String... options) throws Exception {
        Path input = tempDir.resolve("input.txt");
        Path output = tempDir.resolve("output.txt");
        Files.write(input, text.getBytes(StandardCharsets.UTF_8));
        List<String> args = new ArrayList<>(Arrays.asList("convert", "-c", "t2s", "-i", input.toString(), "-o", output.toString()));
        args.addAll(Arrays.asList(options));
        Result result = execute("", args.toArray(new String[0]));
        assertEquals(0, result.code, result.err);
        return new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
    }

    private static void writeZip(Path path, String entryName, String content) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static String readZip(Path path, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile()); InputStream input = zip.getInputStream(zip.getEntry(entryName))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString("UTF-8");
        }
    }

    private static Result execute(String stdin, String... args) throws Exception {
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (PrintStream stdout = new PrintStream(out, true, "UTF-8"); PrintStream stderr = new PrintStream(err, true, "UTF-8")) {
            System.setIn(new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
            System.setOut(stdout);
            System.setErr(stderr);
            CommandLine cli = new CommandLine(new Main())
                    .setOut(new PrintWriter(stdout, true)).setErr(new PrintWriter(stderr, true));
            int code = cli.execute(args);
            return new Result(code, out.toString("UTF-8"), err.toString("UTF-8"));
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static final class Result {
        final int code;
        final String out, err;
        Result(int code, String out, String err) { this.code = code; this.out = out; this.err = err; }
    }
}