package io.github.xdstorage.resource;

/**
 * The operation a staged {@link XdStorageObjectChange} applies to one object — what the
 * trigger manager dispatches on at commit time and what the commit journal redo records.
 * Two parallel constant sets exist: the lower-case names (with the historical docs) and
 * upper-case aliases; consumers test both spellings, so they are interchangeable.
 */
public enum XdStorageObjectOperationType {

    /**
     * Trigger with this type will performed then object was inserted by commit
     * transaction.
     */
    Insert,

    /**
     * Trigger with this type will performed then object was updated by commit
     * transaction.
     */
    Update,

    /**
     * Trigger with this type will performed then object was deleted by commit
     * transaction.
     */
    Delete,
    /** Upper-case alias of {@code Update}, treated identically by consumers. */
    UPDATE,
    /** Upper-case alias of {@code Insert}, treated identically by consumers. */
    INSERT,
    /** Upper-case alias of {@code Delete}, treated identically by consumers. */
    DELETE;

}
