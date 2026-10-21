package io.github.xdstorage.idgeneration;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.transaction.IXdStorageTransaction;

import java.util.UUID;

public class MyStringIdGenerator implements IXdStorageIdGenerator {

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, IXdStorageTransaction transaction) {
        return UUID.randomUUID().toString();
    }
}
