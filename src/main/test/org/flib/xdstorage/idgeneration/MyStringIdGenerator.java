package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.UUID;

public class MyStringIdGenerator implements IXdStorageIdGenerator {

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, IXdStorageTransaction transaction) {
        return UUID.randomUUID().toString();
    }
}
