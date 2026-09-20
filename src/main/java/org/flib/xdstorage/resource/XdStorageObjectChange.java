package org.flib.xdstorage.resource;

public class XdStorageObjectChange {

    public final XdStorageObjectOperationType type;

    public final Object id;

    public final Object oldObject;

    public final Object newObject;

    public XdStorageObjectChange(final XdStorageObjectOperationType type, final Object id,
                                 final Object oldObject, final Object newObject) {
        this.type = type;
        this.id = id;
        this.oldObject = oldObject;
        this.newObject = newObject;
    }
}
