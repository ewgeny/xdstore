package io.github.xdstorage.idgeneration;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.transaction.IXdStorageTransaction;

import java.util.UUID;

/** Generates UUID strings ({@code UUID.randomUUID()}) — the generator behind
 * {@code UUID_GENERATOR}; String id fields only. */
public class XdStorageStringIdGenerator implements IXdStorageIdGenerator {

    /** Stateless — one instance serves every class. */
    public XdStorageStringIdGenerator() {
    }

    /** A fresh random UUID string, unique per call. */
    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) {
        return UUID.randomUUID().toString();
    }
}
