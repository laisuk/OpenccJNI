package openccjnicli;

import openccjni.OpenCC;
import openccjni.OfficeHelper;
import openccjni.TextConverter;
import picocli.CommandLine.*;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subcommand for converting Office documents using OpenCC.
 */
@Command(name = "office", description = "\033[1;34mConvert Office documents using OpenccJNI\033[0m", mixinStandardHelpOptions = true)
public class OfficeCommand implements java.util.concurrent.Callable<Integer> {

    @Option(names = {"-i", "--input"}, paramLabel = "<file>", description = "Input Office file", required = true)
    private File input;

    @Option(names = {"-o", "--output"}, paramLabel = "<file>", description = "Output Office file")
    private File output;

    @Option(
            names = {"-c", "--config"},
            paramLabel = "<conversion>",
            required = true,
            completionCandidates = CliUtils.ConfigCandidates.class,
            description = "Conversion configuration. Supported: ${COMPLETION-CANDIDATES}"
    )
    private String config;

    @Option(names = {"-p", "--punct"}, description = "Punctuation conversion (default: false)")
    private boolean punct;

    @Option(names = {"-f", "--format"}, paramLabel = "<format>", description = "Target Office format (e.g., docx, xlsx, pptx, odt, epub)")
    private String format;

    @Option(names = {"-k", "--keep-font"}, defaultValue = "false", negatable = true, description = "Preserve font-family info (default: false)")
    private boolean keepFont;

    @Option(
            names = {"-F", "--convert-filename"},
            description = "Convert the output filename using the selected OpenCC configuration."
    )
    private boolean convertFilename;

    @Option(
            names = {"-n", "--norm-compat"},
            description = "Normalize CJK Compatibility Ideographs before conversion."
    )
    private boolean normCompat;

    @Option(
            names = {"-E", "--norm-compat-extended"},
            description = "Normalize extended Unicode compatibility/allograph forms and CJK Compatibility Ideographs before conversion."
    )
    private boolean normCompatExtended;

    @Option(
            names = "--detofu",
            paramLabel = "<level>",
            description = "Apply tofu-safe fallback after conversion: all, ext-b, ext-c, ext-d, ext-e, ext-f, ext-g, ext-h, ext-i"
    )
    private String detofu;

    @Option(
            names = {"-D", "--custom-dict"},
            paramLabel = "<slot:mode:path>",
            split = ",",
            completionCandidates = CliUtils.SlotCandidates.class,
            description = "Apply custom dictionary file. Format: slot:append|override:path. Can be repeated or comma-separated. Supported slots: ${COMPLETION-CANDIDATES}"
    )
    private List<String> customDictSpecs;

    private static final Logger LOGGER = Logger.getLogger(OfficeCommand.class.getName());

    @Override
    public Integer call() {
        try {
            CliUtils.validateInputFile(input);
            String inputName = removeExtension(input.getName());
            String ext = getExtension(input.getName());

            String officeFormat;

            if (format != null) {
                officeFormat = format.toLowerCase(java.util.Locale.ROOT);

                if (!OfficeHelper.OFFICE_FORMATS.contains(officeFormat)) {
                    System.err.println("❌ Unsupported Office format: " + format);
                    return 1;
                }
            } else {
                if (ext.isEmpty() || !OfficeHelper.OFFICE_FORMATS.contains(ext.substring(1).toLowerCase(java.util.Locale.ROOT))) {
                    System.err.println("❌ Cannot infer Office format from input file extension.");
                    return 1;
                }

                officeFormat = ext.substring(1).toLowerCase(java.util.Locale.ROOT);
            }

            try (OpenCC opencc = CliUtils.createOpenCC(config, customDictSpecs)) {

                TextConverter textConverter = CliUtils.createTextConverter(
                        opencc,
                        punct,
                        normCompat,
                        normCompatExtended,
                        detofu
                );

                if (output == null) {
                    String outputName = inputName;

                    if (convertFilename) {
                        outputName = textConverter.convert(outputName);
                    }

                    String defaultName = outputName + "_converted." + officeFormat;
                    output = new File(input.getParentFile(), defaultName);
                    System.err.println("ℹ️ Output file not specified. Using: " + output);
                }

                if (getExtension(output.getName()).isEmpty()) {
                    output = new File(output.getAbsolutePath() + "." + officeFormat);
                    System.err.println("ℹ️ Auto-extension applied: " + output.getAbsolutePath());
                }

                OfficeHelper.FileResult result = OfficeHelper.convert(
                        input,
                        output,
                        officeFormat,
                        textConverter,
                        keepFont
                );

                if (result.success) {
                    System.err.println(result.message + "\n\uD83D\uDCC1 Output saved to: " + output.getAbsolutePath());
                } else {
                    System.err.println("❌ Office document conversion failed: " + result.message);
                    return 1;
                }
            }
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("❌ " + e.getMessage());
            return 1;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error during Office document conversion", ex);
            System.err.println("❌ Exception occurred: " + ex.getMessage());
            return 1;
        }
    }

    private String removeExtension(String filename) {
        int idx = filename.lastIndexOf(".");
        return (idx != -1) ? filename.substring(0, idx) : filename;
    }

    private String getExtension(String filename) {
        int idx = filename.lastIndexOf(".");
        return (idx != -1) ? filename.substring(idx) : "";
    }
}
