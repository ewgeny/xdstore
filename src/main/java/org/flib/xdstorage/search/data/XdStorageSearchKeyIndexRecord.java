package org.flib.xdstorage.search.data;

import org.flib.xdstorage.annotations.XdStorageObjectId;

public class XdStorageSearchKeyIndexRecord {

    @XdStorageObjectId
    private Object objectId;

    private Object value;

    public XdStorageSearchKeyIndexRecord() {
        // do nothing
    }

    public XdStorageSearchKeyIndexRecord(final Object objectId, final Object value) {
        this.objectId = objectId;
        this.value = value;
    }

    public Object getObjectId() {
        return objectId;
    }

    public void setObjectId(Object objectId) {
        this.objectId = objectId;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
