package org.flib.xdstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Инкапсулирует управление критическими секциями и флагами rollback-only транзакции.
 */
public class XdStorageTransactionCriticalSection {

    private final String transactionId;
    private final AtomicBoolean rollbackOnly = new AtomicBoolean(false);
    private final AtomicLong criticalCounter = new AtomicLong(0);

    public XdStorageTransactionCriticalSection(final String transactionId) {
        this.transactionId = transactionId;
    }

    public boolean isRollbackOnly() {
        return rollbackOnly.get();
    }

    public void setRollbackOnly() {
        synchronized (criticalCounter) {
            while (criticalCounter.get() > 0) {
                try {
                    criticalCounter.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            rollbackOnly.set(true);
        }
    }

    public void start(boolean rollback) {
        synchronized (criticalCounter) {
            if (!rollback && rollbackOnly.get()) {
                throw new XdStorageRuntimeException("Транзакция " + transactionId + " помечена как rollback-only и будет отменена.");
            }
            criticalCounter.incrementAndGet();
        }
    }

    public void finish() {
        synchronized (criticalCounter) {
            if (criticalCounter.decrementAndGet() == 0) {
                criticalCounter.notifyAll();
            }
        }
    }
}
