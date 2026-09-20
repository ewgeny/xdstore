package org.flib.xdstorage.sqlstorage.transaction;

import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionManager;
import java.util.UUID;

/**
 * Исправленная реализация менеджера распределенных реляционных транзакций.
 */
public class XdStorageSQLTransactionManager extends XdStorageTransactionManager {

    private final XdStorageSQLProcessor processor;

    public XdStorageSQLTransactionManager(final XdStorageServicesLocator services, final XdStorageSQLProcessor processor) {
        super(services);
        this.processor = processor;
    }

    public XdStorageTransaction startTransaction(final long timeout) {
        final long threadId = Thread.currentThread().getId();
        final String txId = UUID.randomUUID().toString();

        // Создаем транзакцию с привязкой к нашему процессору команд и сессии
        final XdStorageSQLTransaction transaction = new XdStorageSQLTransaction(
                this.processor, this, String.valueOf(threadId), null, timeout, txId
        );

        transactions.put(txId, transaction);
        threadsToTransactions.put(threadId, txId);
        return transaction;
    }

    @Override
    public void registerRollbackOnlyTransaction(final XdStorageTransaction transaction) {
        if (transaction != null) {
            services.getExecutor().submit(() -> {
                transaction.rollbackInternal();
            });
        }
    }
}
