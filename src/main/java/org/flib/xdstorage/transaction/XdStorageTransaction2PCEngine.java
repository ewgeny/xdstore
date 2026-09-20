package org.flib.xdstorage.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class XdStorageTransaction2PCEngine {

    private static final Logger log = LogManager.getLogger(XdStorageTransaction2PCEngine.class);

    private XdStorageTransaction2PCEngine() {}

    public static void executeCommit(
            final XdStorageTransaction tx,
            final Queue<XdStorageTransaction.ComparableResourceObject> forCommit,
            final List<IXdStorageResourceObject> lockedResources,
            final Map<Object, IXdStorageResourceObject> resources,
            final XdStorageCommitTransactionStateHolder state,
            final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) throws XdStorageException {

        state.setState(XdStorageCommitTransactionState.PREPARING);

        final Iterator<XdStorageTransaction.ComparableResourceObject> it = forCommit.iterator();
        while (it.hasNext()) {
            final IXdStorageResourceObject res = it.next().resource;
            if (!res.hasChanges(tx)) {
                it.remove();
                continue;
            }
            res.lockForCommit(tx);
            lockedResources.add(res);
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final AtomicReference<XdStorageRuntimeException> exceptionRef = new AtomicReference<>(null);

        XdStorageTransaction.ComparableResourceObject resObject;
        while ((resObject = forCommit.poll()) != null) {
            if (hasError.get()) {
                break;
            }
            try {
                final IXdStorageResourceObject res = resObject.resource;
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(res);
                firstPhaseCommittedResources.put(res.getResourceId(), record);
                res.performFirstPhaseCommit(tx, record);
            } catch (final Throwable e) {
                hasError.set(true);
                exceptionRef.set(new XdStorageRuntimeException("Сбой на фазе Prepare ресурса ID: " + resObject.resource.getResourceId(), e));
                break;
            }
        }

        if (exceptionRef.get() != null) {
            throw exceptionRef.get();
        }

        state.setState(XdStorageCommitTransactionState.PREPARED);

        try {
            resources.values().parallelStream().forEach(resource -> {
                if (hasError.get()) {
                    return;
                }
                try {
                    final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(resource);
                    resource.performSecondPhaseCommit(tx, record);
                } catch (final Throwable e) {
                    log.error("Критический сбой на фазе Commit ресурса " + resource.getResourceId(), e);
                    if (!hasError.getAndSet(true)) {
                        exceptionRef.set(new XdStorageRuntimeException("Сбой на фазе Commit: " + resource.getResourceId(), e));
                    }
                }
            });

            if (exceptionRef.get() != null) {
                throw exceptionRef.get();
            }

            state.setState(XdStorageCommitTransactionState.FINISHED);
        } finally {
            lockedResources.parallelStream().forEach(res -> {
                res.unlockAfterCommit(tx);
            });
        }
    }
}
