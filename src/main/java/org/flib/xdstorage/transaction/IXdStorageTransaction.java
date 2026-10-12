package org.flib.xdstorage.transaction;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageException;

public interface IXdStorageTransaction {

    IXdStorageTransactionManager<?> getTransactionManager();

    long getTimestart();

    IXdStorage getStorage();

    String getTransactionThreadId();

    String getTransactionId();

    long getTimeout();

    void commit();

    void rollback();

    void markRollbackOnly();

    boolean isRollbackOnly();
}
