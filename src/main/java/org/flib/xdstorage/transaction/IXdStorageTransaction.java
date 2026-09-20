package org.flib.xdstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageException;

public interface IXdStorageTransaction {

    String getTransactionThreadId();

    String getTransactionId();

    long getTimeout();

    void commit();

    void rollback();

    void markRollbackOnly();

    boolean isRollbackOnly();
}
