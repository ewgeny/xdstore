package org.flib.xdstorage.code;

public interface IXdStorageSimpleWrapper {

    Object getObjectId__();

    void setReference__(boolean reference);

    boolean isReference__();

    void lock__();

    void unlock__();
}
