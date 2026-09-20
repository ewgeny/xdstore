package org.flib.xdstorage.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class XdStorageTransaction implements IXdStorageTransaction {

    private static final Logger log = LogManager.getLogger(XdStorageTransaction.class);

    private final IXdStorageTransactionManager manager;

    private final long timeout;

    private final String transactionThreadId;

    private final String transactionId;

    private final Long timestart;

    private final Set<Object> resourcesIds;

    protected final Map<Object, IXdStorageResourceObject> resources;

    protected final Queue<ComparableResourceObject> forCommit;

    protected final List<IXdStorageResourceObject> lockedResources;

    private final AtomicReference<TransactionState> transactionState = new AtomicReference<>(TransactionState.InProcess);

    private final XdStorageTransaction globalTransaction;

    private final Set<String> internalTransactions = Collections.synchronizedSet(new HashSet<>());

    private final AtomicBoolean hasInternalTransactions = new AtomicBoolean(false);

    private final AtomicBoolean rollbackOnly = new AtomicBoolean(false);

    private final AtomicLong critical = new AtomicLong(0);

    protected XdStorageTransaction(final IXdStorageTransactionManager manager, final String transactionThreadId,
                                   final XdStorageTransaction globalTransaction, final long timeout, final String transactionId) {
        this.manager = manager;
        this.transactionThreadId = transactionThreadId;
        this.globalTransaction = globalTransaction;
        this.timeout = timeout;
        this.transactionId = transactionId;
        this.timestart = System.nanoTime();
        this.resourcesIds = Collections.synchronizedSet(new HashSet<>());
        this.resources = new ConcurrentHashMap<>();
        this.forCommit = new PriorityBlockingQueue<>();
        this.lockedResources = new ArrayList<>();
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
                    throw new XdStorageRuntimeException("invalid state of transaction " + transactionId, e);
                }
            }
        }
    }

    public XdStorageTransaction getGlobalTransaction() {
        return globalTransaction;
    }

    public String getGlobalTransactionId() {
        return globalTransaction != null ? globalTransaction.getTransactionId() : null;
    }

    public final void suspend(final String internalTransactionId) {
        synchronized (hasInternalTransactions) {
            internalTransactions.add(internalTransactionId);
            hasInternalTransactions.set(true);
        }
    }

    public final void skipOrWaitForInternalTransactions() {
        if (hasInternalTransactions.get()) {
            synchronized (hasInternalTransactions) {
                while (hasInternalTransactions.get()) {
                    try {
                        hasInternalTransactions.wait();
                    } catch (final InterruptedException e) {
                        throw new XdStorageRuntimeException(e);
                    }
                }
            }
        }
    }

    public final void resume(final String internalTransactionId) {
        synchronized (hasInternalTransactions) {
            internalTransactions.remove(internalTransactionId);
            if (internalTransactions.isEmpty()) {
                hasInternalTransactions.set(false);
                hasInternalTransactions.notifyAll();
            }
        }
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public String getTransactionThreadId() {
        return transactionThreadId;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    public long getTimestart() {
        return timestart;
    }

    public void startCriticalSection() throws XdStorageException {
        startCriticalSection(false);
    }

    public void startCriticalSection(boolean rollback) {
        synchronized (critical) {
            if (!rollback && rollbackOnly.get())
                throw new XdStorageRuntimeException("transaction " + transactionId + " marked as rollback only and will be rolled back");
            critical.incrementAndGet();
        }
    }

    public void finishCriticalSection() {
        synchronized (critical) {
            if (critical.decrementAndGet() == 0) {
                critical.notifyAll();
            }
        }
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly.get();
    }

    private void setRollbackOnly() {
        synchronized (critical) {
            while (critical.get() > 0) {
                try {
                    critical.wait();
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            rollbackOnly.set(true);
        }
    }

    @Override
    public void commit() {
        manager.commitTransaction(this);
    }

    @Override
    public void rollback() {
        manager.rollbackTransaction(this);
    }

    public Collection<IXdStorageResourceObject> getResources() {
        return new ArrayList<>(resources.values());
    }

    public boolean isResourceRegistered(final IXdStorageResourceObject resource) {
        return resourcesIds.contains(resource.getResourceId());
    }

    public void registerResource(final IXdStorageResourceObject resource, final int commitOrder) throws XdStorageException {
        synchronized (critical) {
            if (rollbackOnly.get())
                throw new XdStorageException("transaction " + transactionId + " marked as rollback only and will be rolled back");

            resourcesIds.add(resource.getResourceId());
            resources.put(resource.getResourceId(), resource);
            forCommit.add(new ComparableResourceObject(resource, commitOrder));  // use priority queue
        }
    }

    public void commitInternal(final XdStorageCommitTransactionStateHolder state,
                        final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) throws XdStorageException {

        final XdStorageRuntimeException[] exceptions = new XdStorageRuntimeException[]{null};
        final AtomicBoolean hasError = new AtomicBoolean();

        final Collection<IXdStorageResourceObject> resourceToCommit = resources.values();

        // performing first phase commit
        state.setState(XdStorageCommitTransactionState.PREPARING);
        final Iterator<ComparableResourceObject> it = forCommit.iterator();
        while (it.hasNext()) {
            final ComparableResourceObject resObject = it.next();
            final IXdStorageResourceObject res = resObject.resource;
            if (!res.hasChanges(this)) {
                it.remove();
                continue;
            }
            res.lockForCommit(this);
            lockedResources.add(res);
        }


        ComparableResourceObject resObject;
        while ((resObject = forCommit.poll()) != null) {
            if (hasError.get()) {
                break;
            }

            try {
                final IXdStorageResourceObject res = resObject.resource;
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(res);
                // for file system configuration we have to build resource before performing first phase commit,
                // because exception can be thrown by writing resource
                firstPhaseCommittedResources.put(res.getResourceId(), record);
                res.performFirstPhaseCommit(this, record);
            } catch (final Throwable e) {
                if (hasError.get()) {
                    break;
                }
                hasError.set(true);
                exceptions[0] = new XdStorageRuntimeException(e);
            }
        };

        if (exceptions[0] != null)
            throw exceptions[0];

        state.setState(XdStorageCommitTransactionState.PREPARED);

        // performing second phase commit
        resourceToCommit.parallelStream().forEach(resource -> {
            if (hasError.get()) {
                return;
            }

            try {
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(resource);
                resource.performSecondPhaseCommit(this, record);
            } catch (final Throwable e) {
                if (hasError.get()) {
                    return;
                }
                hasError.set(true);
                exceptions[0] = new XdStorageRuntimeException(e);
            }
        });

        if (exceptions[0] != null)
            throw exceptions[0];

        state.setState(XdStorageCommitTransactionState.FINISHED);

        lockedResources.parallelStream().forEach(resource -> {
            resource.unlockAfterCommit(XdStorageTransaction.this);
        });
    }

    public boolean rollbackFailedCommit(final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) {
        final List<XdStorageRuntimeException> exceptions = Collections.synchronizedList(new ArrayList<>());

        firstPhaseCommittedResources.entrySet().parallelStream().forEach(entry -> {
            try {
                resources.get(entry.getKey()).rollbackPerformingFirstPhaseCommit(this, entry.getValue().getChangesObjects());
            } catch (final Throwable e) {
                exceptions.add(new XdStorageRuntimeException(e));
            }
        });

        lockedResources.parallelStream().forEach(resource -> {
            resource.unlockAfterCommit(XdStorageTransaction.this);
        });

        if (!exceptions.isEmpty()) {
            exceptions.stream().forEach(e -> {
                log.error("rollback failed commit error", e);
            });
        }

        return exceptions.isEmpty();
    }

    public void rollbackInternal() {
        for (final IXdStorageResourceObject resource : resources.values()) {
            try {
                resource.rollback(this);
            } catch (final XdStorageException e) {
                throw new XdStorageRuntimeException("invalid state of database", e);
            }
        }

        lockedResources.parallelStream().forEach(resource -> {
            resource.unlockAfterCommit(XdStorageTransaction.this);
        });
    }

    @Override
    public void markRollbackOnly() {
        if (globalTransaction == null || globalTransaction.isRollbackOnly()) {
            setRollbackOnly();
            internalTransactions.stream().collect(Collectors.toList()).parallelStream().forEach(transactionId -> {
                final XdStorageTransaction transaction = (XdStorageTransaction) manager.getTransaction(transactionId);
                transaction.setRollbackOnly();
                transaction.markRollbackOnly();
            });
            manager.registerRollbackOnlyTransaction(this);
        } else if (isRollbackOnly()) {
            internalTransactions.stream().collect(Collectors.toList()).parallelStream().forEach(transactionId -> {
                final XdStorageTransaction transaction = (XdStorageTransaction) manager.getTransaction(transactionId);
                transaction.setRollbackOnly();
                transaction.markRollbackOnly();
            });
            manager.registerRollbackOnlyTransaction(this);
        } else {
            globalTransaction.markRollbackOnly();
        }
    }

    public boolean isTransaction(final XdStorageTransaction transaction) {
        return this == transaction;
    }

    protected class ComparableResourceObject implements Comparable<ComparableResourceObject> {

        public final IXdStorageResourceObject resource;

        private final Integer priority;

        ComparableResourceObject(final IXdStorageResourceObject resource, final int priority) {
            this.resource = resource;
            this.priority = priority;
        }

        @Override
        public int compareTo(ComparableResourceObject o) {
            return this.priority.compareTo(o.priority);
        }
    }

    private enum TransactionState {
        InProcess, Finished;
    }
}
