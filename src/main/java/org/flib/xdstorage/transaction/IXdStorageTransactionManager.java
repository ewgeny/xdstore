package org.flib.xdstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageException;

public interface IXdStorageTransactionManager<T extends IXdStorageTransaction> {

    T beginTransaction(long timeout);

    T beginTransaction(T transaction, long timeout);

    T getTransaction(String transactionId);

    void commitTransaction(T transaction);

    void rollbackTransaction(T transaction);

    T getCurrentTransaction();

    boolean isTransactionAlive(T transaction);

    void registerRollbackOnlyTransaction(T transaction);
}
