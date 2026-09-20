package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.io.Writer;
import java.util.Collection;

public interface IXdStorageObjectsWriter {

    void writeReferences(Writer writer, XdStorageObjectIdField field, Collection<Object> references) throws XdStorageIOException;

    void writeObjects(Writer writer, Collection<Object> objects) throws XdStorageIOException;
}
