package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.io.Reader;
import java.util.Collection;

public interface IXdStorageObjectsReader {

    Collection<Object> readReferences(Reader reader, XdStorageObjectIdField field) throws XdStorageIOException, XdStorageException;

    Collection<Object> read(Reader reader) throws XdStorageIOException, XdStorageException;

    // ДОБАВЛЯЕМ СИГНАТУРУ С ПОДДЕРЖКОЙ ТРАНЗАКЦИИ ДЛЯ ИДЕНТИЧНОСТИ КЭША
    Collection<Object> read(Reader reader, IXdStorageTransaction transaction) throws XdStorageIOException;

    Collection<XdStorageIdentifiableObject> readData(Reader xmlReader, XdStorageObjectIdField field) throws XdStorageIOException, XdStorageException;
}
