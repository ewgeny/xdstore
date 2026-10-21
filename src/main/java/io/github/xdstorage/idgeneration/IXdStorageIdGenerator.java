package io.github.xdstorage.idgeneration;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;

public interface IXdStorageIdGenerator {

    Object generate(Class<?> cl, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException;

}
