package openccjni;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes custom dictionary files to apply to one OpenCC dictionary slot.
 *
 * <p>Each file must be UTF-8 text with one mapping per line in the form
 * {@code source<TAB>target}. Empty lines and lines beginning with {@code #} or
 * {@code //} are ignored. When a target contains whitespace, only its first
 * token is used.</p>
 *
 * <p>This value object is immutable. Its path list is defensively copied.
 * Files themselves are read exactly once when the receiving {@link OpenCC}
 * instance is constructed; later file changes do not affect that converter.</p>
 *
 * @see OpenCC#OpenCC(java.util.List)
 * @see OpenCC#OpenCC(OpenccConfig, java.util.List)
 * @since 1.4.0
 */
public final class CustomDictSpec {
    /** Dictionary slot that receives the custom mappings. */
    public final DictSlot slot;

    /** Immutable, ordered list of UTF-8 dictionary file paths. */
    public final List<Path> paths;

    /** Merge behavior applied to {@link #slot}. */
    public final CustomDictMode mode;

    private CustomDictSpec(
            DictSlot slot,
            List<Path> paths,
            CustomDictMode mode) {

        this.slot = Objects.requireNonNull(slot, "slot");
        this.paths = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(paths, "paths")));
        this.mode = Objects.requireNonNull(mode, "mode");

        if (this.paths.isEmpty()) {
            throw new IllegalArgumentException("paths must not be empty");
        }

        for (Path path : this.paths) {
            Objects.requireNonNull(path, "paths cannot contain null");
        }
    }

    /**
     * Creates a specification for one UTF-8 custom dictionary file.
     *
     * @param slot dictionary slot to modify; must not be {@code null}
     * @param path UTF-8 dictionary file; must not be {@code null}
     * @param mode merge behavior; must not be {@code null}
     * @return immutable custom dictionary specification
     * @throws NullPointerException if any argument is {@code null}
     */
    public static CustomDictSpec fromFile(
            DictSlot slot,
            Path path,
            CustomDictMode mode) {

        return new CustomDictSpec(
                slot,
                Collections.singletonList(
                        Objects.requireNonNull(path, "path")),
                mode);
    }

    /**
     * Creates a specification for multiple UTF-8 dictionary files.
     *
     * <p>Files are read in list order and their mappings are combined before
     * the selected mode is applied.</p>
     *
     * @param slot dictionary slot to modify; must not be {@code null}
     * @param paths ordered dictionary file paths; must not be {@code null},
     *              empty, or contain {@code null}
     * @param mode merge behavior; must not be {@code null}
     * @return immutable custom dictionary specification
     * @throws NullPointerException if {@code slot}, {@code paths},
     *                              {@code mode}, or a path is {@code null}
     * @throws IllegalArgumentException if {@code paths} is empty
     */
    public static CustomDictSpec fromFiles(
            DictSlot slot,
            List<Path> paths,
            CustomDictMode mode) {

        return new CustomDictSpec(slot, paths, mode);
    }
}