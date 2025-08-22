package opencc;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class OpenccWrapper implements AutoCloseable {
    static {
        System.loadLibrary("opencc_fmmseg_capi");
    }

    // Native methods
    private native long opencc_new();

    private native byte[] opencc_convert(long instance, byte[] input, byte[] config, boolean punctuation);

    private native boolean opencc_get_parallel(long instance);

    private native void opencc_set_parallel(long instance, boolean is_parallel);

    private native int opencc_zho_check(long instance, byte[] input);

    private native void opencc_delete(long instance);

    private native String opencc_last_error();

    private long instance;

    private static final List<String> configList = Arrays.asList(
            "s2t", "t2s", "s2tw", "tw2s", "s2twp", "tw2sp", "s2hk", "hk2s",
            "t2tw", "t2twp", "t2hk", "tw2t", "tw2tp", "hk2t", "t2jp", "jp2t"
    );

    // Constructor
    public OpenccWrapper() {
        instance = opencc_new();
        if (instance == 0) {
            throw new RuntimeException("Failed to create OpenCC instance: " + getLastError());
        }
    }

    public String convert(String input, String config, boolean punctuation) {
        if (input.isEmpty()) return "";
        if (!configList.contains(config)) config = "s2t";

        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        byte[] configBytes = config.getBytes(StandardCharsets.UTF_8);
        byte[] rawOutput = opencc_convert(instance, inputBytes, configBytes, punctuation);

        if (rawOutput == null) {
            throw new RuntimeException("Conversion failed: " + getLastError());
        }

        return new String(rawOutput, StandardCharsets.UTF_8);
    }

    public boolean isParallel() {
        return opencc_get_parallel(instance);
    }

    public void setParallel(boolean isParallel) {
        opencc_set_parallel(instance, isParallel);
    }

    public int zhoCheck(String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        return opencc_zho_check(instance, inputBytes);
    }

    public String getLastError() {
        String last_error = opencc_last_error();
        return Objects.requireNonNullElse(last_error, "");
    }

    @Override
    public void close() {
        if (instance != 0) {
            opencc_delete(instance);
            instance = 0;
        }
    }
}
