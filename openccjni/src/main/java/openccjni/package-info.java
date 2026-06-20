/**
 * Java bindings for OpenCC Chinese text conversion.
 *
 * <p>{@link openccjni.OpenCC} is the primary high-level API. Its static conversion methods
 * are safe for concurrent use because each thread receives its own native wrapper. Configured
 * {@code OpenCC} instances are mutable and should be confined to one thread unless callers
 * provide external synchronization.</p>
 *
 * <p>The package also provides strongly typed conversion profiles through
 * {@link openccjni.OpenccConfig}, Office and EPUB document conversion through
 * {@link openccjni.OfficeHelper}, and lower-level JNI integration classes for advanced use.</p>
 *
 * @since 1.0.0
 */
package openccjni;
