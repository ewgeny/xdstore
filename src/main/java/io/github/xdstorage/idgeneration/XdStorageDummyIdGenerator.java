package io.github.xdstorage.idgeneration;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;

public class XdStorageDummyIdGenerator implements IXdStorageIdGenerator {

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        return null;
    }
}
