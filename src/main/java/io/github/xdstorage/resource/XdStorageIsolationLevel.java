package io.github.xdstorage.resource;

/** The read isolation applied to every resource cache of a storage
 * (open-time configuration: {@code XdStorageConfig.builder().isolationLevel(...)},
 * default {@link #SNAPSHOT}). */
public enum XdStorageIsolationLevel {
    /** Repeatable-read/snapshot: a reader that started before another's commit keeps
     * seeing the older version for its whole transaction (the default). */
    SNAPSHOT,
    /** Read committed: a reader always sees the LATEST committed state. */
    READ_COMMITTED
}
