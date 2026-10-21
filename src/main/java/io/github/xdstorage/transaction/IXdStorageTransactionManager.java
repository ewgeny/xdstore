package io.github.xdstorage.transaction;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.exceptions.XdStorageException;

public interface IXdStorageTransactionManager<T extends IXdStorageTransaction> {

    IXdStorage getStorage();

    T beginTransaction(long timeout);

    T beginTransaction(T transaction, long timeout);

    T getTransaction(String transactionId);

    void commitTransaction(T transaction);

    void rollbackTransaction(T transaction);

    T getCurrentTransaction();

    boolean isTransactionAlive(T transaction);

    void registerRollbackOnlyTransaction(T transaction);
}
