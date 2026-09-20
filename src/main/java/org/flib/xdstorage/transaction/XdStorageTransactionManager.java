package org.flib.xdstorage.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.services.XdStorageServicesLocator;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class XdStorageTransactionManager implements IXdStorageTransactionManager<XdStorageTransaction> {

    private static final Logger log = LogManager.getLogger(XdStorageTransactionManager.class);

    protected final XdStorageServicesLocator services;

    private final Map<String, XdStorageTransaction> transactionsById;

    private final Map<String, String> threadsByTransactionId;

    private final Map<String, XdStorageTransaction> transactionsByThreadId;

    public XdStorageTransactionManager(final XdStorageServicesLocator provider) {
        this.services = provider;
        this.transactionsById = new ConcurrentHashMap<>();
        this.threadsByTransactionId = new ConcurrentHashMap<>();
        this.transactionsByThreadId = new ConcurrentHashMap<>();
    }

    @Override
    public boolean isTransactionAlive(final XdStorageTransaction transaction) {
        return this.transactionsById.containsKey(transaction.getTransactionId()) && !transaction.isRollbackOnly();
    }

    @Override
    public XdStorageTransaction beginTransaction(final long timeout) {
        final String currentThreadId = getCurrentThreadId();

        final XdStorageTransaction transaction = transactionsByThreadId.get(currentThreadId);

        return beginTransaction(transaction, timeout);
    }

    @Override
    public XdStorageTransaction beginTransaction(final XdStorageTransaction transaction, long timeout) {
        final String currentThreadId = getCurrentThreadId();

        final XdStorageTransaction newTransaction;
        final String newTransactionId = generateTransactionId();
        if (transaction == null) {
            newTransaction = createTransaction(this, currentThreadId, timeout, newTransactionId);
        } else if (transaction.isRollbackOnly()) {
            transaction.waitForFinish();
            newTransaction = createTransaction(this, currentThreadId, timeout, newTransactionId);
        } else {
            newTransaction = createTransaction(this, currentThreadId, transaction, timeout, newTransactionId);
            transaction.suspend(newTransaction.getTransactionId());
        }
        threadsByTransactionId.put(newTransactionId, currentThreadId);
        transactionsByThreadId.put(currentThreadId, newTransaction);
        transactionsById.put(newTransactionId, newTransaction);

        log.info(String.format("Started transaction %s", newTransactionId));

        return newTransaction;
    }

    private XdStorageTransaction createTransaction(final XdStorageTransactionManager manager, final String threadId,
                                                   final long timeout, final String transactionId) {
        return createTransaction(manager, threadId, null, timeout, transactionId);
    }

    protected XdStorageTransaction createTransaction(final XdStorageTransactionManager manager, final String threadId,
                                                     final XdStorageTransaction globalTransaction, final long timeout, final String transactionId) {
        return new XdStorageTransaction(manager, threadId, globalTransaction, timeout, transactionId);
    }

    public void commitTransaction(final XdStorageTransaction transaction) {
        if (transaction.isRollbackOnly()) {
            throw new XdStorageRuntimeException("transaction will be rolled back because it is marked as rollback only");
        }

        final XdStorageTransaction globalTransactionToCheck = transaction.getGlobalTransaction();
        if (globalTransactionToCheck != null && globalTransactionToCheck.isRollbackOnly()) {
            transaction.markRollbackOnly();
            throw new XdStorageRuntimeException("transaction should be rolled back because global transaction is marked as rollbacl only");
        }


        final Collection<IXdStorageResourceObject> resources = transaction.getResources();

        // there is locked idgenerator for objects with policy StoreWithParentObject and database id generation strategy,
        // because in this time another transaction is in commiting state and it is locked transaction manager
        final XdStorageCommitTransactionStateHolder state = new XdStorageCommitTransactionStateHolder();
        final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources = new ConcurrentHashMap<>();

        transaction.skipOrWaitForInternalTransactions();

        try {
            transaction.startCriticalSection();

            transaction.commitInternal(state, firstPhaseCommittedResources);

            if (state.getState() == XdStorageCommitTransactionState.FINISHED) {
                fireChanges(firstPhaseCommittedResources, transaction);
            }
        } catch (final Throwable e) {
            log.warn("commit internal error", e);

            transaction.rollbackFailedCommit(firstPhaseCommittedResources);
        } finally {
            transaction.finishCriticalSection();
        }

        threadsByTransactionId.remove(transaction.getTransactionId());
        transactionsById.remove(transaction.getTransactionId());

        final String currentThreadId = transaction.getTransactionThreadId();
        transactionsByThreadId.remove(currentThreadId);

        resources.parallelStream().forEach(resource -> {
            resource.release(transaction);
        });

        services.getCloner().release(transaction);

        final String globalTransactionId = transaction.getGlobalTransactionId();
        if (globalTransactionId != null) {
            final XdStorageTransaction globalTransaction = transactionsById.get(globalTransactionId);
            if (globalTransaction.getTransactionThreadId().equalsIgnoreCase(currentThreadId)) {
                threadsByTransactionId.put(globalTransactionId, currentThreadId);
                transactionsByThreadId.put(currentThreadId, globalTransaction);
            }
            globalTransaction.resume(transaction.getTransactionId());
        }

        transaction.markFinished();

        log.info(String.format("Committed transaction %s", transaction.getTransactionId()));
    }

    protected void fireChanges(final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources, final XdStorageTransaction transaction) {
        final Callable<Boolean> task = () -> {
            firstPhaseCommittedResources.entrySet().stream().forEach(entry -> {
                entry.getValue().performTriggers(transaction, services.getTriggersManager());
            });
            return true;
        };

        try {
            services.getExecutor().submit(task).get();
        } catch (final InterruptedException | ExecutionException e) {
            log.warn("performing triggers error", e);
        }
    }

    public void rollbackTransaction(final XdStorageTransaction transaction) {
        if (transaction.isRollbackOnly()) {
            log.warn("transaction " + transaction.getTransactionId() + " will be rolled back automatically by mark rollback only");
            return;
        }

        final String globalTransactionId = transaction.getGlobalTransactionId();
        if (globalTransactionId != null) {
            transaction.markRollbackOnly();
            return;
        }

        final Collection<IXdStorageResourceObject> resources = transaction.getResources();

        transaction.skipOrWaitForInternalTransactions();

        transaction.startCriticalSection(true);
        try {
            transaction.rollbackInternal();
        } finally {
            transaction.finishCriticalSection();
        }

        resources.parallelStream().forEach(resource -> {
            resource.release(transaction);
        });

        services.getCloner().release(transaction);

        threadsByTransactionId.remove(transaction.getTransactionId());
        transactionsById.remove(transaction.getTransactionId());

        final String currentThreadId = transaction.getTransactionThreadId();
        transactionsByThreadId.remove(currentThreadId);

        transaction.markFinished();

        log.info(String.format("Rolled back transaction %s", transaction.getTransactionId()));
    }

    @Override
    public XdStorageTransaction getCurrentTransaction() {
        return transactionsByThreadId.get(getCurrentThreadId());
    }

    @Override
    public XdStorageTransaction getTransaction(final String transactionId) {
        return transactionsById.get(transactionId);
    }

    @Override
    public void registerRollbackOnlyTransaction(final XdStorageTransaction transaction) {
        services.getExecutor().submit(() -> {
            if (transactionsById.containsKey(transaction.getTransactionId()) && transaction.isRollbackOnly()) {
                final Collection<IXdStorageResourceObject> resources = transaction.getResources();

                transaction.skipOrWaitForInternalTransactions();

                transaction.rollbackInternal();

                resources.parallelStream().forEach(resource -> {
                    resource.release(transaction);
                });

                services.getCloner().release(transaction);

                threadsByTransactionId.remove(transaction.getTransactionId());
                transactionsById.remove(transaction.getTransactionId());

                final String currentThreadId = transaction.getTransactionThreadId();
                transactionsByThreadId.remove(currentThreadId);

                final String globalTransactionId = transaction.getGlobalTransactionId();
                if (globalTransactionId != null) {
                    final XdStorageTransaction globalTransaction = transactionsById.get(globalTransactionId);
                    if (globalTransaction.getTransactionThreadId().equalsIgnoreCase(currentThreadId)) {
                        threadsByTransactionId.put(globalTransactionId, currentThreadId);
                        transactionsByThreadId.put(currentThreadId, globalTransaction);
                    }
                    globalTransaction.resume(transaction.getTransactionId());
                }

                transaction.markFinished();

                log.info("Rolled back transaction " + transaction.getTransactionId() + " by mark rollback only");
            }
        });
    }

    private static String generateTransactionId() {
        return UUID.randomUUID().toString();
    }

    private static String getCurrentThreadId() {
        final Thread thread = Thread.currentThread();
        return thread.getName() + thread.getId();
    }
}
