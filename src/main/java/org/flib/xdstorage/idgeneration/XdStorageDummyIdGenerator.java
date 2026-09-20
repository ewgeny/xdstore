package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

public class XdStorageDummyIdGenerator implements IXdStorageIdGenerator {

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        return null;
    }
}
