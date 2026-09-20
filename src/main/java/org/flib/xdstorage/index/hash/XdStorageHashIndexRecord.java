package org.flib.xdstorage.index.hash;

import org.flib.xdstorage.annotations.XdStorageObjectId;

public class XdStorageHashIndexRecord {

    @XdStorageObjectId
    private Object objectId;

    private Object resourceId;

    public XdStorageHashIndexRecord() {
        // do nothing
    }

    public XdStorageHashIndexRecord(final Object objectId, final Object resourceId) {
        this.objectId = objectId;
        this.resourceId = resourceId;
    }

    public Object getObjectId() {
        return objectId;
    }

    public void setObjectId(final Object objectId) {
        this.objectId = objectId;
    }

    public Object getResourceId() {
        return resourceId;
    }

    public void setResourceId(final Object resourceId) {
        this.resourceId = resourceId;
    }

}
