package io.github.xdstorage.helpers;

/**
 * SPI for marshalling "simple" attribute types — the values the engine stores as
 * self-describing scalars rather than as referenced aggregate bodies (primitives and
 * their wrappers, {@code String}, {@code Date}, {@code Class} tokens, enums, and any
 * type the implementation elects to treat as simple, e.g. id types constructible from
 * a {@code String}). The engine resolves the active implementation through
 * {@code XdStorageServicesLocator}; {@link XdStorageDefaultSimpleTypeHelper} is the
 * built-in default. The serialization codecs call this helper symmetrically on write
 * ({@link #simpleTypeToString}) and on read ({@link #simpleTypeFromString}).
 *
 * <p>Implementations must be stateless or otherwise thread-safe: a single instance is
 * shared by every reader and writer of a storage. Engine-internal — not part of the
 * public API.</p>
 */
public interface IXdStorageSimpleTypeHelper {

    /**
     * Whether the given value of the given declared type is a "simple" scalar — i.e.
     * round-trippable through {@link #simpleTypeToString}/{@link #simpleTypeFromString}
     * instead of being walked as an object graph.
     *
     * @param cl     the declared type of the attribute (may be a primitive class)
     * @param object the runtime value being classified (used only as a hint; may be {@code null}
     *               when the caller knows the value is non-null by construction)
     * @return {@code true} when the engine must serialize {@code object} as a simple scalar
     */
    boolean isSimpleType(final Class<?> cl, final Object object);

    /**
     * Renders a simple value as the canonical string stored on disk. The rendering must be
     * invertible by {@link #simpleTypeFromString} for the same declared type.
     *
     * @param object the simple value to render (never {@code null} when invoked by the engine)
     * @return the canonical string form of {@code object}
     */
    String simpleTypeToString(final Object object);

    /**
     * Restores a simple value from its on-disk string form — the inverse of
     * {@link #simpleTypeToString}. The default implementation materializes primitives and
     * enums dynamically and, as a last resort, constructs the value through a
     * {@code String}-argument constructor of the target type.
     *
     * @param cl    the declared target type (may be a primitive class)
     * @param value the string previously produced by {@link #simpleTypeToString}
     * @return the restored value, or {@code null} when the value cannot be materialized
     *         (for example an unknown enum constant or class name)
     */
    Object simpleTypeFromString(final Class<?> cl, final String value);
}
