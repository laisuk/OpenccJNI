// src/test/java/openccjni/OpenCCParallelModeTest.java
package openccjni;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.DisplayName.class)
class OpenCCParallelModeTest {

    private boolean original;

    @BeforeEach
    void captureOriginal() {
        original = OpenCC.isParallel();
    }

    @AfterEach
    void restoreOriginal() {
        // Ensure we leave the current thread's wrapper state as we found it.
        OpenCC.setParallel(original);
    }

    @Test
    @DisplayName("1) setParallel(true) enables parallel mode for the current thread")
    void enablesParallel() {
        OpenCC.setParallel(false);
        assertFalse(OpenCC.isParallel(), "precondition: expected false");

        OpenCC.setParallel(true);
        assertTrue(OpenCC.isParallel(), "parallel mode should be enabled");
    }

    @Test
    @DisplayName("2) setParallel(false) disables parallel mode for the current thread")
    void disablesParallel() {
        OpenCC.setParallel(true);
        assertTrue(OpenCC.isParallel(), "precondition: expected true");

        OpenCC.setParallel(false);
        assertFalse(OpenCC.isParallel(), "parallel mode should be disabled");
    }

    @Test
    @DisplayName("3) setParallel is idempotent")
    void idempotent() {
        OpenCC.setParallel(true);
        assertTrue(OpenCC.isParallel());
        OpenCC.setParallel(true);
        assertTrue(OpenCC.isParallel(), "setting true twice remains true");

        OpenCC.setParallel(false);
        assertFalse(OpenCC.isParallel());
        OpenCC.setParallel(false);
        assertFalse(OpenCC.isParallel(), "setting false twice remains false");
    }

    @Test
    @DisplayName("4) isParallel reflects the current thread's most recent setting")
    void reflectsMostRecentSet() {
        OpenCC.setParallel(false);
        assertFalse(OpenCC.isParallel());

        OpenCC.setParallel(true);
        assertTrue(OpenCC.isParallel());

        OpenCC.setParallel(false);
        assertFalse(OpenCC.isParallel());
    }
}
