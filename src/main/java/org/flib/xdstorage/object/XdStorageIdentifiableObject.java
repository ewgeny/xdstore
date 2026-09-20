package org.flib.xdstorage.object;

import org.flib.xdstorage.annotations.XdStorageObjectId;

import java.util.Objects;

public class XdStorageIdentifiableObject extends XdStorageObject {

    @XdStorageObjectId
    private Object id;

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final XdStorageIdentifiableObject that = (XdStorageIdentifiableObject) o;
        return Objects.equals(id, that.id) && super.equals(o);
    }
}
