package openccjni;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenCCCustomDictionaryTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultConstructorsStillConvert() {
        try (OpenCC defaultConfig = new OpenCC();
             OpenCC namedConfig = new OpenCC("s2t");
             OpenCC enumConfig = new OpenCC(OpenccConfig.S2T)) {
            assertEquals("漢字", defaultConfig.convert("汉字"));
            assertEquals("漢字", namedConfig.convert("汉字"));
            assertEquals("漢字", enumConfig.convert("汉字"));
        }
    }

    @Test
    void customDictionaryIsLoadedOnceAndUsed() throws Exception {
        Path dictionary = dictionary("custom.txt", "测试术语\t自訂結果\n");
        CustomDictSpec spec = CustomDictSpec.fromFile(
                DictSlot.STPhrases, dictionary, CustomDictMode.Append);

        try (OpenCC opencc = new OpenCC(OpenccConfig.S2T, Collections.singletonList(spec))) {
            Files.delete(dictionary);
            assertEquals("這是自訂結果", opencc.convert("这是测试术语"));
        }
    }

    @Test
    void instancesKeepDifferentDictionariesIndependent() throws Exception {
        CustomDictSpec first = customSpec("first.txt", "测试术语\t第一結果\n");
        CustomDictSpec second = customSpec("second.txt", "测试术语\t第二結果\n");

        try (OpenCC firstOpencc = new OpenCC(OpenccConfig.S2T, Collections.singletonList(first));
             OpenCC secondOpencc = new OpenCC(OpenccConfig.S2T, Collections.singletonList(second))) {
            assertEquals("第一結果", firstOpencc.convert("测试术语"));
            assertEquals("第二結果", secondOpencc.convert("测试术语"));
            assertEquals("第一結果", firstOpencc.convert("测试术语"));
        }
    }

    @Test
    void oneCustomInstanceSupportsConcurrentConversion() throws Exception {
        CustomDictSpec spec = customSpec("concurrent.txt", "测试术语\t並行結果\n");
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try (OpenCC opencc = new OpenCC(OpenccConfig.S2T, Collections.singletonList(spec))) {
            CountDownLatch start = new CountDownLatch(1);
            @SuppressWarnings("unchecked")
            Future<String>[] futures = new Future[32];
            for (int i = 0; i < futures.length; i++) {
                futures[i] = executor.submit(() -> {
                    start.await();
                    String result = "";
                    for (int attempt = 0; attempt < 100; attempt++) {
                        result = opencc.convert("这是测试术语");
                    }
                    return result;
                });
            }

            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("這是並行結果", future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void useAfterCloseFails() throws Exception {
        CustomDictSpec spec = customSpec("closed.txt", "测试术语\t關閉結果\n");

        try (OpenCC opencc = new OpenCC(OpenccConfig.S2T, Collections.singletonList(spec))) {
            opencc.close();
            assertThrows(IllegalStateException.class, () -> opencc.convert("测试术语"));
        }
    }

    private CustomDictSpec customSpec(String name, String contents) throws Exception {
        return CustomDictSpec.fromFile(
                DictSlot.STPhrases,
                dictionary(name, contents),
                CustomDictMode.Append);
    }

    private Path dictionary(String name, String contents) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        return path;
    }
}
