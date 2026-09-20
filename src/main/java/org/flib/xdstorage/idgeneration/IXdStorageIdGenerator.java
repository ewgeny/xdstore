package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

public interface IXdStorageIdGenerator {

    Object generate(Class<?> cl, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException;

}
