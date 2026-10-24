package io.github.xdstorage.resource;

/**
 * One staged change of a single object within a resource — the record the commit collector
 * carries from phase 1 to phase 2 and, on failure, to compensation (journal redo entries are
 * built from these). Value object: instances are immutable after construction.
 *
 * <p>Engine-internal — not part of the public API.</p>
 */
public class XdStorageObjectChange {

    /** The operation this change applies (insert, update or delete). */
    public final XdStorageObjectOperationType type;

    /** The id of the affected object within its resource. */
    public final Object id;

    /** The pre-image of the object ({@code null} for inserts). */
    public final Object oldObject;

    /** The post-image of the object ({@code null} for deletes). */
    public final Object newObject;

    /**
     * Builds a change record.
     *
     * @param type      the operation type
     * @param id        the id of the affected object
     * @param oldObject the pre-image ({@code null} for inserts)
     * @param newObject the post-image ({@code null} for deletes)
     */
    public XdStorageObjectChange(final XdStorageObjectOperationType type, final Object id,
                                 final Object oldObject, final Object newObject) {
        this.type = type;
        this.id = id;
        this.oldObject = oldObject;
        this.newObject = newObject;
    }
}
