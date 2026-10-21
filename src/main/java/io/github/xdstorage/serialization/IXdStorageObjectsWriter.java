package io.github.xdstorage.serialization;

import io.github.xdstorage.exceptions.XdStorageIOException;
import io.github.xdstorage.utils.XdStorageObjectIdField;

import java.io.Writer;
import java.util.Collection;

public interface IXdStorageObjectsWriter {

    void writeReferences(Writer writer, XdStorageObjectIdField field, Collection<Object> references) throws XdStorageIOException;

    void writeObjects(Writer writer, Collection<Object> objects) throws XdStorageIOException;
}
