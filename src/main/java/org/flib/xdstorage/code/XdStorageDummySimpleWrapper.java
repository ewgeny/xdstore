package org.flib.xdstorage.code;

public class XdStorageDummySimpleWrapper implements IXdStorageSimpleWrapper {

    public static boolean isDummySimpleWrapper() {
        return true;
    }

    @Override
    public Object getObjectId__() {
        return null;
    }

    @Override
    public void setReference__(boolean reference) {
        // do nothing
    }

    @Override
    public boolean isReference__() {
        // do nothing
        return false;
    }

    @Override
    public void lock__() {
        // do nothing
    }

    @Override
    public void unlock__() {
        // do nothing
    }
}
