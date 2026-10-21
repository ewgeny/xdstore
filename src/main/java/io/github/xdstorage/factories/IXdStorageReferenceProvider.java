package io.github.xdstorage.factories;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.code.IXdStorageSimpleWrapper;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.transaction.XdStorageTransaction;
import io.github.xdstorage.utils.XdStorageObjectIdField;

public interface IXdStorageReferenceProvider {

    IXdStorageSimpleWrapper getReference(Class<?> cl, Object objectId, IXdStorageTransaction transaction);

    IXdStorageSimpleWrapper createAndRegisterReference(Class<?> cl, XdStorageObjectIdField idField, Object objectId, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException;

    void release(XdStorageTransaction transaction);
}
