package io.github.xdstorage.idgeneration;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.transaction.IXdStorageTransaction;

import java.util.UUID;

public class XdStorageStringIdGenerator implements IXdStorageIdGenerator {

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) {
        return UUID.randomUUID().toString();
    }
}
