package io.github.xdstorage.serialization;

import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageIOException;
import io.github.xdstorage.object.XdStorageIdentifiableObject;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.utils.XdStorageObjectIdField;

import java.io.Reader;
import java.util.Collection;

public interface IXdStorageObjectsReader {

    Collection<Object> readReferences(Reader reader, XdStorageObjectIdField field) throws XdStorageIOException, XdStorageException;

    Collection<Object> read(Reader reader) throws XdStorageIOException, XdStorageException;

    // ДОБАВЛЯЕМ СИГНАТУРУ С ПОДДЕРЖКОЙ ТРАНЗАКЦИИ ДЛЯ ИДЕНТИЧНОСТИ КЭША
    Collection<Object> read(Reader reader, IXdStorageTransaction transaction) throws XdStorageIOException;

    Collection<XdStorageIdentifiableObject> readData(Reader xmlReader, XdStorageObjectIdField field) throws XdStorageIOException, XdStorageException;
}
