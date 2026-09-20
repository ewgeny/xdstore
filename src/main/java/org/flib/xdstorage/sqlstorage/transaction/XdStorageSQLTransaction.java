package org.flib.xdstorage.sqlstorage.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class XdStorageSQLTransaction extends XdStorageTransaction {

    private static final Logger log = LogManager.getLogger(XdStorageSQLTransaction.class);

    private final XdStorageSQLProcessor processor;

    protected XdStorageSQLTransaction(final XdStorageSQLProcessor processor, final IXdStorageTransactionManager manager,
                                      final String transactionThreadId, final XdStorageSQLTransaction globalTransaction,
                                      final long timeout, final String transactionId) {
        super(manager, transactionThreadId, globalTransaction, timeout, transactionId);

        this.processor = processor;
    }

    @Override
    public void commitInternal(final XdStorageCommitTransactionStateHolder state,
                                  final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) {

        final XdStorageRuntimeException[] exceptions = new XdStorageRuntimeException[]{null};
        final AtomicBoolean hasError = new AtomicBoolean();

        final Collection<IXdStorageResourceObject> resourceToCommit = resources.values();

        try {
            processor.executeAlter(this);
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("alter phase error", e);
        }

        // performing first phase commit
        state.setState(XdStorageCommitTransactionState.PREPARING);
        ComparableResourceObject resObject;
        while ((resObject = forCommit.poll()) != null) {
            if (hasError.get()) {
                break;
            }

            try {
                final IXdStorageResourceObject res = resObject.resource;
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(res);
                res.performFirstPhaseCommit(this, record);
                firstPhaseCommittedResources.put(res.getResourceId(), record);
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

        try {
            processor.execute(this);
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("first phase error", e);
        }

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

        try {
            processor.execute(this);
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("second phase error", e);
        }

        state.setState(XdStorageCommitTransactionState.FINISHED);
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

        if (!exceptions.isEmpty()) {
            exceptions.stream().forEach(e -> {
                log.error("rollback failed commit error", e);
            });
        } else {
            try {
                processor.execute(this);
            } catch (final Throwable e) {
                log.error("rollback failed commit error", e);
                exceptions.add(new XdStorageRuntimeException(e));
            }
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

        try {
            processor.execute(this);
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("rollback error", e);
        }
    }
}
