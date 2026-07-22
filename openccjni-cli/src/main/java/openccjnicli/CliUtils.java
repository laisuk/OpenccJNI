package openccjnicli;

import openccjni.CustomDictMode;
import openccjni.CustomDictSpec;
import openccjni.DictSlot;
import openccjni.OpenCC;
import openccjni.OpenccConfig;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared parsing and construction utilities for OpenccJNI CLI commands. */
public final class CliUtils {
    private static final Map<String, DictSlot> SLOT_LOOKUP = createSlotLookup();

    private CliUtils() {
    }

    /** Supplies Picocli help and completion from OpenCC's canonical config list. */
    @SuppressWarnings("NullableProblems")
    static final class ConfigCandidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return OpenCC.getSupportedConfigs().iterator();
        }
    }

    // ------------------------------------------------------------------------
    // OpenCC construction
    // ------------------------------------------------------------------------

    static OpenCC createOpenCC(String config, List<String> customDictSpecs) {
        OpenccConfig typedConfig = OpenccConfig.tryParse(config);
        if (typedConfig == null) {
            typedConfig = OpenccConfig.defaultConfig();
        }

        if (customDictSpecs == null || customDictSpecs.isEmpty()) {
            return new OpenCC(typedConfig);
        }

        List<CustomDictSpec> specs = new ArrayList<>(customDictSpecs.size());
        for (String raw : customDictSpecs) {
            specs.add(parseCustomDictSpec(raw));
        }

        return new OpenCC(typedConfig, specs);
    }

    // ------------------------------------------------------------------------
    // Custom dictionary option parsing
    // ------------------------------------------------------------------------

    static CustomDictSpec parseCustomDictSpec(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty --custom-dict spec");
        }

        // A limit of three preserves colons in the path, including Windows drive letters.
        String[] parts = raw.split(":", 3);
        if (parts.length != 3 || parts[2].trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid --custom-dict spec: " + raw
                            + " (expected slot:append|override:path)"
            );
        }

        return CustomDictSpec.fromFile(
                parseDictSlot(parts[0]),
                Paths.get(parts[2].trim()),
                parseCustomDictMode(parts[1])
        );
    }

    static DictSlot parseDictSlot(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Custom dict slot must not be null");
        }

        DictSlot slot = SLOT_LOOKUP.get(normalize(value));
        if (slot == null) {
            throw new IllegalArgumentException("Invalid custom dict slot: " + value);
        }
        return slot;
    }

    static CustomDictMode parseCustomDictMode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Custom dict mode must not be null");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("append".equals(normalized)) {
            return CustomDictMode.Append;
        }
        if ("override".equals(normalized)) {
            return CustomDictMode.Override;
        }

        throw new IllegalArgumentException("Invalid custom dict mode: " + value);
    }

    private static Map<String, DictSlot> createSlotLookup() {
        Map<String, DictSlot> map = new HashMap<>();
        for (DictSlot slot : DictSlot.values()) {
            map.put(normalize(slot.name()), slot);
        }
        return Collections.unmodifiableMap(map);
    }

    private static String normalize(String value) {
        return value
                .trim()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
    }
}