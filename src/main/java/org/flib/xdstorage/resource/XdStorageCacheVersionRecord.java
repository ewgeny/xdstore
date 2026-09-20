package org.flib.xdstorage.resource;

import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Декомпозированная структура версии записи в кэше СУБД.
 * Управляет конкурентными транзакционными блокировками и состояниями MVCC.
 */
public class XdStorageCacheVersionRecord {

    public Object id;
    public Object object;
    public Object newObject;
    public XdStorageTransaction transaction;
    public long timestamp = Long.MIN_VALUE;

    public XdStorageResourceCache.Change change = XdStorageResourceCache.Change.undefined;
    public XdStorageResourceCache.Change previousChange = XdStorageResourceCache.Change.undefined;
    public XdStorageResourceCache.State state = XdStorageResourceCache.State.undefined;
    public XdStorageResourceCache.State previousState = XdStorageResourceCache.State.undefined;

    public boolean firstPhaseCommit;
    public boolean secondPhaseCommit;
    private final AtomicBoolean locked = new AtomicBoolean(false);

    /**
     * ИСПРАВЛЕНИЕ: Использование ConcurrentHashMap вместо обычного HashMap
     * для безопасного отслеживания таймаутов блокировок в параллельных потоках.
     */
    private final Map<String, Long> blockingTime = new ConcurrentHashMap<>();

    public boolean canBeChangedByTransaction(final XdStorageTransaction transaction) {
        return this.state == XdStorageResourceCache.State.undefined
                || (this.state == XdStorageResourceCache.State.locked && this.transaction == transaction)
                || (this.state == XdStorageResourceCache.State.committed && transaction.getTimestart() > timestamp);
    }

    public boolean canBeChangedByTransaction(final XdStorageTransaction transaction, final long readTimestamp) {
        return this.state == XdStorageResourceCache.State.undefined
                || (this.state == XdStorageResourceCache.State.locked && this.transaction == transaction)
                || (this.state == XdStorageResourceCache.State.committed && readTimestamp > timestamp);
    }

    public boolean isChangedByTransaction(final XdStorageTransaction transaction) {
        return this.change != XdStorageResourceCache.Change.read && this.transaction != null && this.transaction == transaction;
    }

    public Object getObject() {
        return this.object;
    }

    public Object getNewObject() {
        return this.newObject;
    }

    public void setNewObject(final Object newObject) {
        this.newObject = newObject;
    }

    public Object getId() {
        return this.id;
    }

    public boolean isCommitedState() {
        return this.state == XdStorageResourceCache.State.committed;
    }

    public boolean isReadChange() {
        return this.change == XdStorageResourceCache.Change.read;
    }

    public boolean isInsertChange() {
        return this.change == XdStorageResourceCache.Change.insert;
    }

    public boolean isUpdateChange() {
        return this.change == XdStorageResourceCache.Change.update;
    }

    public boolean isDeleteChange() {
        return this.change == XdStorageResourceCache.Change.delete;
    }

    public void markUpdate() {
        this.previousChange = this.change;
        this.change = XdStorageResourceCache.Change.update;
        this.previousState = this.state;
        this.state = XdStorageResourceCache.State.locked;
    }

    public void markDelete() {
        this.previousChange = this.change;
        this.change = XdStorageResourceCache.Change.delete;
        this.previousState = this.state;
        this.state = XdStorageResourceCache.State.locked;
    }

    public void lock(final XdStorageTransaction transaction) throws XdStorageException {
        if (this.transaction != null && this.transaction == transaction) {
            return;
        }
        synchronized (locked) {
            while (locked.get()) {
                try {
                    final String txId = transaction.getTransactionId();
                    final Long startTime = blockingTime.remove(txId);
                    final Long currentTime = System.currentTimeMillis();

                    if (startTime == null) {
                        blockingTime.put(txId, currentTime);
                    } else if ((currentTime - startTime) >= transaction.getTimeout()) {
                        throw new XdStorageException("Транзакция " + txId + " прервана по таймауту ожидания блокировки объекта ID: " + id);
                    } else {
                        blockingTime.put(txId, startTime);
                    }
                    locked.wait(transaction.getTimeout() / 2);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new XdStorageException("Поток прерван при ожидании блокировки", e);
                }
            }
            locked.set(true);
            this.transaction = transaction;
        }
    }

    public void unlock(final XdStorageTransaction transaction) throws XdStorageException {
        if (this.transaction == transaction) {
            this.transaction = null;
            synchronized (locked) {
                locked.set(false);
                locked.notifyAll();
            }
        } else {
            throw new XdStorageException("Попытка снять чужую блокировку транзакцией " + transaction.getTransactionId());
        }
    }

    public void prepareCommit() {
        firstPhaseCommit = true;
    }

    public void performCommit() {
        secondPhaseCommit = true;
    }

    public void commit() {
        if (!firstPhaseCommit || !secondPhaseCommit) {
            throw new XdStorageRuntimeException("Невалидное состояние двухфазного коммита для записи ID: " + id);
        }
        this.object = this.newObject;
        this.newObject = null;
        this.state = XdStorageResourceCache.State.committed;
        this.previousState = XdStorageResourceCache.State.undefined;
        this.firstPhaseCommit = false;
        this.secondPhaseCommit = false;
        this.timestamp = System.nanoTime();
    }

    public void rollback() {
        this.newObject = null;
        this.state = this.previousState;
        this.previousState = XdStorageResourceCache.State.undefined;
        this.change = previousChange;
        this.previousChange = XdStorageResourceCache.Change.undefined;
        this.firstPhaseCommit = false;
        this.secondPhaseCommit = false;
    }
}
