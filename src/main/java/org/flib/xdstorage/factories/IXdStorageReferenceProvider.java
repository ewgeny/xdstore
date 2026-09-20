package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public interface IXdStorageReferenceProvider {

    IXdStorageSimpleWrapper getReference(Class<?> cl, Object objectId, IXdStorageTransaction transaction);

    IXdStorageSimpleWrapper createAndRegisterReference(Class<?> cl, XdStorageObjectIdField idField, Object objectId, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException;

    void release(XdStorageTransaction transaction);
}
