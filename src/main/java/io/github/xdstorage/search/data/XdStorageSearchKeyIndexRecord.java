package io.github.xdstorage.search.data;

import io.github.xdstorage.annotations.XdStorageObjectId;

/** The key-tree record: the id-to-primary-value back-translation of one indexed object.
 * The per-index {@code key_} B+tree maps the object id to this record, so update and delete
 * can re-derive the search-tree key from the id alone and a repeated save can detect that
 * the object is already indexed (the idempotency check of the insert path). Like the search
 * tree itself, this is an entity-derived copy — a redundancy that keeps the index usable
 * even when the data journal behind it is torn, per the power-loss matrix.
 *
 * <p>A plain mutable POJO — not thread-safe. Engine-internal — not part of the public API. */
public class XdStorageSearchKeyIndexRecord {

    @XdStorageObjectId
    private Object objectId;

    private Object value;

    /** Creates an empty record, for the deserializer. */
    public XdStorageSearchKeyIndexRecord() {
        // do nothing
    }

    /**
     * Creates a complete record.
     *
     * @param objectId the indexed object's id (the key-tree key)
     * @param value    the PRIMARY index value the object was indexed under
     */
    public XdStorageSearchKeyIndexRecord(final Object objectId, final Object value) {
        this.objectId = objectId;
        this.value = value;
    }

    /**
     * The indexed object's id.
     *
     * @return the object id
     */
    public Object getObjectId() {
        return objectId;
    }

    /**
     * Replaces the indexed object's id.
     *
     * @param objectId the new object id
     */
    public void setObjectId(Object objectId) {
        this.objectId = objectId;
    }

    /**
     * The PRIMARY index value the object was indexed under.
     *
     * @return the primary value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Replaces the PRIMARY index value.
     *
     * @param value the new primary value
     */
    public void setValue(Object value) {
        this.value = value;
    }
}
