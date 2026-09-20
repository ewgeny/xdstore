package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.UUID;

public class XdStorageStringIdGenerator implements IXdStorageIdGenerator {

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) {
        return UUID.randomUUID().toString();
    }
}
