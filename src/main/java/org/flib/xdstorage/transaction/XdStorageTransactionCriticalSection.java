package org.flib.xdstorage.transaction;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class XdStorageTransactionCriticalSection {

    private final AtomicInteger counter = new AtomicInteger(0);
    private final AtomicBoolean rollbackOnly = new AtomicBoolean(false);

    public void enter(final boolean requiresRollbackOnFailure) {
        counter.incrementAndGet();
        if (requiresRollbackOnFailure) {
            rollbackOnly.set(true);
        }
    }

    public boolean exit() {
        return counter.decrementAndGet() == 0;
    }

    public boolean isInside() {
        return counter.get() > 0;
    }

    public boolean isRollbackOnly() {
        return rollbackOnly.get();
    }

    public void forceRollback() {
        rollbackOnly.set(true);
    }

    public void reset() {
        counter.set(0);
        rollbackOnly.set(false);
    }
}
