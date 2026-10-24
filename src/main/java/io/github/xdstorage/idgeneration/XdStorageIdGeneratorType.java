package io.github.xdstorage.idgeneration;

/** Who assigns an entity's primary key (set on the id field via
 * {@code @XdStorageObjectFieldProperties}). */
public enum XdStorageIdGeneratorType {

    /** A custom {@code IXdStorageIdGenerator} class ({@code idGeneratorClass}). */
    CUSTOM_GENERATOR,

    /** The engine's persisted Hi-Lo counter (default): per-class batches are allocated
     * transactionally and served in memory. */
    DATABASE_GENERATOR,
    /** A random UUID String — requires a String id field (a numeric id under this type
     * is a loud contract error, never a silently different id type). */
    UUID_GENERATOR;
}
