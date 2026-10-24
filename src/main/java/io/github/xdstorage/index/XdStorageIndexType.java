package io.github.xdstorage.index;

/**
 * The physical structure of a class id index — how the engine routes an object id to the
 * data fragment holding its record. The structure determines the persistence layout of the
 * id-index resource ({@code XdStorageHashIndexRecord} entries versus a persisted B+tree
 * object) and the cost profile of lookups and scans.
 */
public enum XdStorageIndexType {

    /**
     * Hash id index: a lock-free in-memory id {@code ->} data-fragment map persisted as
     * {@code XdStorageHashIndexRecord} entries — constant-time point lookups, full-walk
     * scans.
     */
    Hash,

    /**
     * B+tree id index: ids live in the leaves of a persisted B+tree object, giving ordered
     * traversal and range scans in addition to point lookups.
     */
    BTree;

}
