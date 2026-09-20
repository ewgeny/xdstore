package org.flib.xdstorage.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Финальная ультра-декомпозированная реализация транзакции базы данных.
 * Освобождена от логики 2PC-протокола, делегируя вызовы специализированным движкам.
 */
public class XdStorageTransaction implements IXdStorageTransaction {

    private static final Logger log = LogManager.getLogger(XdStorageTransaction.class);

    private final IXdStorageTransactionManager manager;
    private final long timeout;
    private final String transactionThreadId;
    private final String transactionId;
    private final Long timestart;
    private final XdStorageTransaction globalTransaction;

    // Изолированные потокобезопасные хранилища транзакционных ресурсов
    private final Set<Object> resourcesIds = Collections.synchronizedSet(new HashSet<>());
    protected final Map<Object, IXdStorageResourceObject> resources = new ConcurrentHashMap<>();
    protected final Queue<ComparableResourceObject> forCommit = new PriorityBlockingQueue<>();
    protected final List<IXdStorageResourceObject> lockedResources = Collections.synchronizedList(new ArrayList<>());

    private final AtomicReference<TransactionState> transactionState = new AtomicReference<>(TransactionState.InProcess);

    // Подключенные легковесные декомпозированные помощники
    private final XdStorageTransactionCriticalSection criticalSection;
    private final XdStorageSubTransactionCoordinator subTransactionCoordinator;

    protected XdStorageTransaction(final IXdStorageTransactionManager manager, final String transactionThreadId,
                                   final XdStorageTransaction globalTransaction, final long timeout, final String transactionId) {
        this.manager = manager;
        this.transactionThreadId = transactionThreadId;
        this.globalTransaction = globalTransaction;
        this.timeout = timeout;
        this.transactionId = transactionId;
        this.timestart = System.nanoTime();

        this.criticalSection = new XdStorageTransactionCriticalSection(transactionId);
        this.subTransactionCoordinator = new XdStorageSubTransactionCoordinator();
    }

    public void markFinished() {
        synchronized (transactionState) {
            transactionState.set(TransactionState.Finished);
            transactionState.notifyAll();
        }
    }

    public void waitForFinish() {
        synchronized (transactionState) {
            while (transactionState.get() != TransactionState.Finished) {
                try {
                    transactionState.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new XdStorageRuntimeException("Поток прерван при ожидании транзакции " + transactionId, e);
                }
            }
        }
    }

    // Делегирование управления подтранзакциями
    public final void suspend(final String internalTxId) { subTransactionCoordinator.suspend(internalTxId); }
    public final void resume(final String internalTxId) { subTransactionCoordinator.resume(internalTxId); }
    public final void skipOrWaitForInternalTransactions() { subTransactionCoordinator.skipOrWaitForInternalTransactions(); }

    // Делегирование критических секций СУБД
    public void startCriticalSection() throws XdStorageException { criticalSection.start(false); }
    public void startCriticalSection(boolean rollback) { criticalSection.start(rollback); }
    public void finishCriticalSection() { criticalSection.finish(); }
    @Override public boolean isRollbackOnly() { return criticalSection.isRollbackOnly(); }

    @Override public void commit() { manager.commitTransaction(this); }
    @Override public void rollback() { manager.rollbackTransaction(this); }

    public long getTimeout() { return timeout; }
    @Override public String getTransactionThreadId() { return transactionThreadId; }
    @Override public String getTransactionId() { return transactionId; }
    public long getTimestart() { return timestart; }
    public XdStorageTransaction getGlobalTransaction() { return globalTransaction; }
    public Collection<IXdStorageResourceObject> getResources() { return new ArrayList<>(resources.values()); }
    public boolean isResourceRegistered(final IXdStorageResourceObject res) { return resourcesIds.contains(res.getResourceId()); }

    public void registerResource(final IXdStorageResourceObject resource, final int commitOrder) throws XdStorageException {
        if (criticalSection.isRollbackOnly()) {
            throw new XdStorageException("Транзакция " + transactionId + " помечена как rollback-only.");
        }
        resourcesIds.add(resource.getResourceId());
        resources.put(resource.getResourceId(), resource);
        forCommit.add(new ComparableResourceObject(resource, commitOrder));
    }

    /**
     * ДЕЛЕГИРОВАНИЕ 2PC: Вызов внешнего специализированного движка двухфазной фиксации.
     */
    public void commitInternal(final XdStorageCommitTransactionStateHolder state,
                               final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) throws XdStorageException {
        XdStorageTransaction2PCEngine.executeCommit(this, forCommit, lockedResources, resources, state, firstPhaseCommittedResources);
    }

    /**
     * ДЕЛЕГИРОВАНИЕ 2PC: Вызов компенсационного отката ресурсов при падении.
     */
    public boolean rollbackFailedCommit(final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) {
        return XdStorageTransaction2PCEngine.executeFailedCommitRollback(this, resources, lockedResources, firstPhaseCommittedResources);
    }

    public void rollbackInternal() {
        for (final IXdStorageResourceObject resource : resources.values()) {
            try {
                resource.rollback(this);
            } catch (final XdStorageException e) {
                throw new XdStorageRuntimeException("Неустранимая ошибка аварийного отката СУБД", e);
            }
        }
        lockedResources.parallelStream().forEach(resource -> resource.unlockAfterCommit(this));
    }

    @Override
    public void markRollbackOnly() {
        if (globalTransaction == null || globalTransaction.isRollbackOnly() || isRollbackOnly()) {
            criticalSection.setRollbackOnly();
            subTransactionCoordinator.getInternalTransactionIds().parallelStream().forEach(txId -> {
                final XdStorageTransaction transaction = (XdStorageTransaction) manager.getTransaction(txId);
                transaction.criticalSection.setRollbackOnly();
                transaction.markRollbackOnly();
            });
            manager.registerRollbackOnlyTransaction(this);
        } else {
            globalTransaction.markRollbackOnly();
        }
    }

    public boolean isTransaction(final XdStorageTransaction transaction) { return this == transaction; }

    /**
     * Компаратор ресурсов: Линейная двухкритериальная сортировка по приоритету и строковому ID (Анти-дедлок).
     */
    protected class ComparableResourceObject implements Comparable<ComparableResourceObject> {
        public final IXdStorageResourceObject resource;
        private final Integer priority;

        ComparableResourceObject(final IXdStorageResourceObject resource, final int priority) {
            this.resource = resource;
            this.priority = priority;
        }

        @Override
        public int compareTo(ComparableResourceObject o) {
            int comp = this.priority.compareTo(o.priority);
            if (comp != 0) return comp;
            return this.resource.getResourceId().toString().compareTo(o.resource.getResourceId().toString());
        }
    }

    private enum TransactionState { InProcess, Finished }
}
