package org.flib.xdstorage.factories.strategies;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

public interface XdStorageClonerTypeProcessor {
    boolean supports(Class<?> clazz, Object value);
    Object cloneAndWrap(Object src, IXdStorage storage, IXdStorageTransaction tx, XdStorageClonerContext context) throws Exception;
    Object unwrapAndClone(Object src, XdStorageClonerContext context) throws Exception;
}
