package org.flib.xdstorage.code;

public class XdStorageDummyUnmodifiableWrapper implements IXdStorageUnmodifiableWrapper {

    public static boolean isDummyUnmodifiableWrapper() {
        return true;
    }
}
