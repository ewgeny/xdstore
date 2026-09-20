package org.flib.xdstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Отвечает за координацию и отслеживание жизненного цикла вложенных подтранзакций.
 */
public class XdStorageSubTransactionCoordinator {

    private final Set<String> internalTransactions = Collections.synchronizedSet(new HashSet<>());
    private final AtomicBoolean hasInternalTransactions = new AtomicBoolean(false);

    public void suspend(final String internalTransactionId) {
        synchronized (hasInternalTransactions) {
            internalTransactions.add(internalTransactionId);
            hasInternalTransactions.set(true);
        }
    }

    public void resume(final String internalTransactionId) {
        synchronized (hasInternalTransactions) {
            internalTransactions.remove(internalTransactionId);
            if (internalTransactions.isEmpty()) {
                hasInternalTransactions.set(false);
                hasInternalTransactions.notifyAll();
            }
        }
    }

    public void skipOrWaitForInternalTransactions() {
        if (hasInternalTransactions.get()) {
            synchronized (hasInternalTransactions) {
                while (hasInternalTransactions.get()) {
                    try {
                        hasInternalTransactions.wait();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new XdStorageRuntimeException("Ожидание вложенных транзакций прервано", e);
                    }
                }
            }
        }
    }

    public Set<String> getInternalTransactionIds() {
        return internalTransactions.stream().collect(Collectors.toSet());
    }
}
