package org.flib.xdstorage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Оптимизированный примитив синхронизации СУБД (Поинт Г).
 * Базируется на ReentrantReadWriteLock с кастомной поддержкой Lock Upgrade для совместимости с MVCC ядром.
 */
public class XdStorageReadWriteLock {

    private static final Logger log = LogManager.getLogger(XdStorageReadWriteLock.class);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    // Трекер потока, который удерживает Read-лок и запрашивает Write-лок
    private volatile Thread upgradeThread;
    private final Object upgradeMonitor = new Object();

    public boolean isWriteLocked() {
        return lock.isWriteLockedByCurrentThread() || (upgradeThread == Thread.currentThread());
    }

    public boolean isReadLocked() {
        return lock.getReadHoldCount() > 0;
    }

    public boolean tryLockWrite() {
        final Thread currentThread = Thread.currentThread();

        // Если этот поток уже держит Read-лок и никто больше не читает — это легитимный Lock Upgrade!
        if (lock.getReadHoldCount() > 0 && lock.getReadLockCount() == lock.getReadHoldCount()) {
            synchronized (upgradeMonitor) {
                if (upgradeThread == null || upgradeThread == currentThread) {
                    upgradeThread = currentThread;
                    return true;
                }
            }
        }
        return lock.writeLock().tryLock();
    }

    public boolean tryLockRead() {
        return lock.readLock().tryLock();
    }

    public void lockWrite(final IXdStorageTransaction transaction) throws InterruptedException {
        final Thread currentThread = Thread.currentThread();
        long timeout = transaction != null ? transaction.getTimeout() : 3000L;

        // Проверяем возможность Lock Upgrade
        if (lock.getReadHoldCount() > 0 && lock.getReadLockCount() == lock.getReadHoldCount()) {
            synchronized (upgradeMonitor) {
                if (upgradeThread == null || upgradeThread == currentThread) {
                    upgradeThread = currentThread;
                    return;
                }
            }
        }

        if (!lock.writeLock().tryLock(timeout, TimeUnit.MILLISECONDS)) {
            throw new XdStorageRuntimeException("Таймаут транзакции при ожидании монопольной блокировки на запись (Write Lock)");
        }
    }

    public void lockRead(final IXdStorageTransaction transaction) throws InterruptedException {
        long timeout = transaction != null ? transaction.getTimeout() : 3000L;

        // Если мы сами держим апгрейд-лок, чтение разрешено без блокировок
        if (upgradeThread == Thread.currentThread()) {
            return;
        }

        if (!lock.readLock().tryLock(timeout, TimeUnit.MILLISECONDS)) {
            throw new XdStorageRuntimeException("Таймаут транзакции при ожидании разделяемой блокировки на чтение (Read Lock)");
        }
    }

    public void unlockWrite() {
        final Thread currentThread = Thread.currentThread();
        if (upgradeThread == currentThread) {
            synchronized (upgradeMonitor) {
                upgradeThread = null;
            }
            return;
        }

        if (!lock.isWriteLockedByCurrentThread()) {
            throw new IllegalMonitorStateException("Поток не удерживает блокировку на запись");
        }
        lock.writeLock().unlock();
    }

    public void unlockRead() {
        if (upgradeThread == Thread.currentThread()) {
            // Если удерживается апгрейд, Read-лок снимется при общем unlockWrite
            return;
        }

        if (lock.getReadHoldCount() == 0) {
            throw new IllegalMonitorStateException("Поток не удерживает блокировку на чтение");
        }
        lock.readLock().unlock();
    }
}
