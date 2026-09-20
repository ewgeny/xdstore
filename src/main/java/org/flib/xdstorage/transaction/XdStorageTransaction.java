package org.flib.xdstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import java.util.*;

/**
 * Исправленная базовая реализация контекста ACID-транзакции СУБД.
 */
public abstract class XdStorageTransaction {

    protected final IXdStorageTransactionManager manager;
    protected final String transactionThreadId;
    protected final XdStorageTransaction globalTransaction;
    protected final String transactionId;
    protected final long timeout;

    // Атомарный координатор критических секций и флагов отката
    protected final XdStorageTransactionCriticalSection criticalSectionHelper = new XdStorageTransactionCriticalSection();

    protected final Map<Object, IXdStorageResourceObject> resources = new java.util.concurrent.ConcurrentHashMap<>();
    protected final Queue<ComparableResourceObject> forCommit = new java.util.concurrent.ConcurrentLinkedQueue<>();
    protected final XdStorageCommitTransactionStateHolder state = new XdStorageCommitTransactionStateHolder();
    protected final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources = new java.util.concurrent.ConcurrentHashMap<>();
    protected final List<IXdStorageResourceObject> lockedResources = Collections.synchronizedList(new ArrayList<>());

    protected XdStorageTransaction(final IXdStorageTransactionManager manager, final String threadId, final XdStorageTransaction global, final long timeout, final String transactionId) {
        this.manager = manager;
        this.transactionThreadId = threadId;
        this.globalTransaction = global;
        this.timeout = timeout;
        this.transactionId = transactionId;
    }

    public String getTransactionId() { return transactionId; }
    public String getTransactionThreadId() { return transactionThreadId; }
    public XdStorageCommitTransactionState getCommitState() { return state.getState(); }
    public boolean isRollbackOnly() { return criticalSectionHelper.isRollbackOnly(); }

    public abstract void rollbackInternal();

    public void startCriticalSection(final boolean requiresRollbackOnFailure) {
        criticalSectionHelper.enter(requiresRollbackOnFailure);
    }

    public void finishCriticalSection() {
        if (criticalSectionHelper.exit() && criticalSectionHelper.isRollbackOnly()) {
            manager.registerRollbackOnlyTransaction(this);
        }
    }

    public void commit() throws XdStorageException {
        startCriticalSection(true);
        try {
            if (state.getState() == XdStorageCommitTransactionState.ACTIVE) {
                XdStorageTransaction2PCEngine.executeCommit(this, forCommit, lockedResources, resources, state, firstPhaseCommittedResources);
            }
        } catch (final Throwable e) {
            criticalSectionHelper.forceRollback();
            if (state.getState() == XdStorageCommitTransactionState.PREPARING || state.getState() == XdStorageCommitTransactionState.PREPARED) {
                XdStorageTransaction2PCEngine.executeFailedCommitRollback(this, resources, lockedResources, firstPhaseCommittedResources);
            }
            throw new XdStorageException("Фатальный сбой двухфазного коммита распределенной транзакции", e);
        } finally {
            finishCriticalSection();
        }
    }

    public void registerResource(final IXdStorageResourceObject resource) {
        if (resource != null && !resources.containsKey(resource.getResourceId())) {
            resources.put(resource.getResourceId(), resource);
            forCommit.add(new ComparableResourceObject(resource));
        }
    }

    public static final class ComparableResourceObject implements Comparable<ComparableResourceObject> {
        public final IXdStorageResourceObject resource;
        public ComparableResourceObject(final IXdStorageResourceObject resource) { this.resource = resource; }
        @Override public int compareTo(final ComparableResourceObject o) {
            return String.valueOf(this.resource.getResourceId()).compareTo(String.valueOf(o.resource.getResourceId()));
        }
    }
}
