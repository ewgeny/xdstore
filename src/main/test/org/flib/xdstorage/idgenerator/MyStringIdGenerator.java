package org.flib.xdstorage.idgenerator;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.UUID;

public class MyStringIdGenerator implements IXdStorageIdGenerator {

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, IXdStorageTransaction transaction) {
        return UUID.randomUUID().toString();
    }
}
