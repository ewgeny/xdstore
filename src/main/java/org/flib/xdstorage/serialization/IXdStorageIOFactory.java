package org.flib.xdstorage.serialization;

public interface IXdStorageIOFactory {

    IXdStorageObjectsReader newInstanceReader();

    IXdStorageObjectsWriter newInstanceWriter();
}
