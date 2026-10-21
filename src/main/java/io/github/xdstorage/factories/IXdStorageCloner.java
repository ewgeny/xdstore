package io.github.xdstorage.factories;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.transaction.XdStorageTransaction;

public interface IXdStorageCloner {

    Object cloneAndWrap(Object toClone, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException;

    Object unwrapAndClone(Object toCloneWrapped) throws XdStorageException;

    void fillAndWrap(Object reference, Object object, IXdStorage storage, XdStorageTransaction transaction) throws XdStorageException;

    void release(XdStorageTransaction transaction);
}
