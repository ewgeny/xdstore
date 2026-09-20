package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;

public interface IXdStorageCloner {

    Object cloneAndWrap(Object toClone, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException;

    Object unwrapAndClone(Object toCloneWrapped) throws XdStorageException;

    void fillAndWrap(Object reference, Object object, IXdStorage storage, XdStorageTransaction transaction) throws XdStorageException;

    void release(XdStorageTransaction transaction);
}
