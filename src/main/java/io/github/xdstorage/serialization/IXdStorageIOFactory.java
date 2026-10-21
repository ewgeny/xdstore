package io.github.xdstorage.serialization;

public interface IXdStorageIOFactory {

    IXdStorageObjectsReader newInstanceReader();

    IXdStorageObjectsWriter newInstanceWriter();
}
