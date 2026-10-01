/**
 * Java 8 bindings for OpenCC Chinese text conversion.
 *
 * <p>{@link openccjni.OpenCC} is the primary high-level API. Its static conversion
 * methods are safe for concurrent use because each thread receives its own native
 * wrapper. Configured instances own native resources: use try-with-resources,
 * confine each instance to one thread, or synchronize access externally.</p>
 *
 * <pre>{@code
 * String traditional = OpenCC.convert("汉字", OpenccConfig.S2T, false);
 *
 * try (OpenCC converter = new OpenCC(OpenccConfig.TW2S)) {
 *     String simplified = converter.convert("繁體字");
 * }
 * }</pre>
 *
 * <p>{@link openccjni.OpenccConfig} provides supported conversion profiles.
 * Configuration parsing trims surrounding whitespace and ignores case.
 * Constructors and configuration setters fall back to {@code s2t} for invalid
 * profiles; static conversion methods return non-empty input unchanged instead.
 * See each overload for its null-input and error contract.</p>
 *
 * <p>{@link openccjni.CustomDictSpec}, {@link openccjni.DictSlot}, and
 * {@link openccjni.CustomDictMode} configure custom UTF-8 dictionaries when an
 * instance is constructed. Normalization and {@link openccjni.DeTofuLevel}
 * fallback operations are independent of its active conversion profile.</p>
 *
 * <p>{@link openccjni.OfficeHelper} converts Office, OpenDocument, and EPUB
 * packages using an OpenCC instance or a {@link openccjni.TextConverter}
 * callback. Callbacks may receive XML/XHTML markup and must preserve escaping.
 * Package conversion failures are reported through result objects.</p>
 *
 * <p>{@link openccjni.OpenccWrapper} exposes lower-level JNI operations.
 * Native libraries are loaded on first use of the wrapper, using the system
 * library search followed by bundled resources. Native errors belong to the
 * calling thread; retrieve {@link openccjni.OpenCC#getLastError()} immediately
 * after the operation being checked.</p>
 *
 * @since 1.0.0
 */
package openccjni;
