package org.flib.xdstorage.factories.strategies;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

/**
 * Контракт сквозного контекста транзакционного клонирования.
 */
public interface XdStorageClonerContext {
    Object processCloneAndWrap(Object value, IXdStorage storage, IXdStorageTransaction tx) throws Exception;
    Object processUnwrapAndClone(Object value) throws Exception;
}
