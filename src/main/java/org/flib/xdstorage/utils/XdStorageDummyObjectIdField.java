package org.flib.xdstorage.utils;

public class XdStorageDummyObjectIdField extends XdStorageObjectIdField {

    public static final XdStorageObjectIdField Instance = new XdStorageDummyObjectIdField();

    private XdStorageDummyObjectIdField() {
        super(null, null, null, null);
    }

    @Override
    public Object get(final Object object) {
        return null;
    }

    @Override
    public void set(final Object object, final Object objectId) {
        // do nothing
    }
}
