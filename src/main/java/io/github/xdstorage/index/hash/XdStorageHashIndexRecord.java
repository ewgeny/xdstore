package io.github.xdstorage.index.hash;

import io.github.xdstorage.annotations.XdStorageObjectId;

/**
 * One id-index record: maps an object id ({@code objectId}) to the id of the data-fragment
 * resource whose file holds the object's record ({@code resourceId}). These records are the
 * persisted content of the hash id-index resource; they are engine-owned and are never
 * stored for user entity classes.
 */
public class XdStorageHashIndexRecord {

    @XdStorageObjectId
    private Object objectId;

    private Object resourceId;

    /** Creates an empty record — the serializer's no-argument form. */
    public XdStorageHashIndexRecord() {
        // do nothing
    }

    /**
     * Creates a fully populated record.
     *
     * @param objectId   the indexed object's id
     * @param resourceId the data-fragment resource id holding the object's record
     */
    public XdStorageHashIndexRecord(final Object objectId, final Object resourceId) {
        this.objectId = objectId;
        this.resourceId = resourceId;
    }

    /**
     * Returns the indexed object's id.
     *
     * @return the indexed object's id
     */
    public Object getObjectId() {
        return objectId;
    }

    /**
     * Sets the indexed object's id.
     *
     * @param objectId the indexed object's id
     */
    public void setObjectId(final Object objectId) {
        this.objectId = objectId;
    }

    /**
     * Returns the data-fragment resource id holding the object's record.
     *
     * @return the data-fragment resource id holding the object's record
     */
    public Object getResourceId() {
        return resourceId;
    }

    /**
     * Sets the data-fragment resource id holding the object's record.
     *
     * @param resourceId the data-fragment resource id holding the object's record
     */
    public void setResourceId(final Object resourceId) {
        this.resourceId = resourceId;
    }

}
