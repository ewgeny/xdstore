package org.flib.xdstorage.utils;

public class XdStorageDummyObjectField extends XdStorageObjectField {

    public static final XdStorageObjectField Instance = new XdStorageDummyObjectField();

    private XdStorageDummyObjectField() {
        super(null, null, null, null);
    }

    @Override
    public <T> T get(Object obj) {
        return null;
    }

    @Override
    public <T> void set(Object obj, T value) {
        // do nothing
    }
}
