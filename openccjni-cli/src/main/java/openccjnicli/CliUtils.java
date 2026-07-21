package openccjnicli;

import openccjni.OpenCC;

import java.util.ArrayList;

/** Shared utilities for the OpenccJNI CLI commands. */
public final class CliUtils {
    private CliUtils() {
    }

    /** Supplies Picocli help and shell completion from OpenCC's canonical config list. */
    public static final class ConfigCandidates extends ArrayList<String> {
        private static final long serialVersionUID = 1L;

        public ConfigCandidates() {
            super(OpenCC.getSupportedConfigs());
        }
    }
}
