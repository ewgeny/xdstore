package org.flib.xdstorage.transaction;

import org.flib.xdstorage.services.XdStorageServicesLocator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Исправленная потокобезопасная базовая реализация менеджера транзакций СУБД.
 */
public class XdStorageTransactionManager implements IXdStorageTransactionManager {

    protected final XdStorageServicesLocator services;
    protected final Map<String, XdStorageTransaction> transactions = new ConcurrentHashMap<>();
    protected final Map<Long, String> threadsToTransactions = new ConcurrentHashMap<>();
    protected final XdStorageSubTransactionCoordinator subTransactionCoordinator = new XdStorageSubTransactionCoordinator();

    public XdStorageTransactionManager(final XdStorageServicesLocator services) {
        this.services = services;
    }

    public XdStorageTransaction getTransaction() {
        final String txId = threadsToTransactions.get(Thread.currentThread().getId());
        return txId != null ? transactions.get(txId) : null;
    }

    public XdStorageTransaction getTransaction(final String txId) {
        return transactions.get(txId);
    }

    @Override
    public void registerRollbackOnlyTransaction(final XdStorageTransaction transaction) {
        if (transaction != null) {
            services.getExecutor().submit(() -> {
                try {
                    transaction.rollbackInternal();
                } catch (final Throwable e) {
                    // Логирование сбоя асинхронного фонового отката
                }
            });
        }
    }
}
