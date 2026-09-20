package org.flib.xdstorage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class XdStorageReadWriteLock {

    private static final Logger log = LogManager.getLogger(XdStorageReadWriteLock.class);

    private AtomicLong writeLocksCounter = new AtomicLong();

    private Thread writeLockThread;

    private Map<Thread, AtomicLong> readLocksCounters = new HashMap<>();

    public boolean isWriteLocked() {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            return writeLocksCounter.get() > 0 && writeLockThread == currentThread;
        }
    }

    public boolean isReadLocked() {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            final AtomicLong readLocksCounter = readLocksCounters.get(currentThread);
            return readLocksCounter != null && readLocksCounter.get() > 0;
        }
    }

    public boolean tryLockWrite() {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            if (writeLockThread != null && writeLockThread != currentThread) {
                log.debug("can't be locked for write because it is locked for write by another thread");
                return false;
            }
            if (readLocksCounters.size() > 0) {
                if (readLocksCounters.size() != 1 || readLocksCounters.get(currentThread) == null) {
                    log.debug("can't be locked for write because it is locked for read by some other threads");
                    return false;
                }
            }
            writeLockThread = currentThread;
            writeLocksCounter.incrementAndGet();
            return true;
        }
    }

    public boolean tryLockRead() {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            if (writeLockThread != null && writeLockThread != currentThread) {
                log.debug("can't be locked for read because it is locked for write by another thread");
                return false;
            }
            AtomicLong counter = readLocksCounters.get(currentThread);
            if (counter == null) {
                readLocksCounters.put(currentThread, counter = new AtomicLong());
            }
            counter.incrementAndGet();
            return true;
        }
    }

    public void lockWrite(final IXdStorageTransaction transaction) throws InterruptedException {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            boolean canBeLocked = (writeLockThread == null || writeLockThread == currentThread)
                    && (readLocksCounters.size() == 0 || (readLocksCounters.size() ==  1 && readLocksCounters.get(currentThread) != null));
            while (!canBeLocked) {
                wait(transaction.getTimeout());
                canBeLocked = (writeLockThread == null || writeLockThread == currentThread)
                        && (readLocksCounters.size() == 0 || (readLocksCounters.size() ==  1 && readLocksCounters.get(currentThread) != null));

                if (log.isDebugEnabled() && !canBeLocked) {
                    if (writeLockThread != null && writeLockThread != currentThread) {
                        log.debug("can't be locked for write because it is locked for write by another thread");
                    }
                    if (readLocksCounters.size() == 1 && readLocksCounters.get(currentThread) == null) {
                        log.debug("can't be locked for write because it is locked for read by another thread");
                    }
                    if (readLocksCounters.size() > 1) {
                        log.debug("can't be locked for write because it is locked for read by some other threads");
                    }
                }
            }
            writeLockThread = currentThread;
            writeLocksCounter.incrementAndGet();
        }
    }

    public void lockRead(final IXdStorageTransaction transaction) throws InterruptedException {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            while (writeLockThread != null) {
                if (writeLockThread == currentThread) {
                    break;
                }
                wait(transaction.getTimeout());

                if (log.isDebugEnabled() && writeLockThread != currentThread) {
                    log.debug("can't be locked for read because it is locked for write by another thread");
                }
            }
            AtomicLong counter = readLocksCounters.get(currentThread);
            if (counter == null) {
                readLocksCounters.put(currentThread, counter = new AtomicLong());
            }
            counter.incrementAndGet();
        }
    }

    public void unlockWrite() {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            if (writeLockThread == null || writeLockThread != currentThread) {
                throw new IllegalMonitorStateException("can't be unlocked: it is not locked by this thread");
            }
            if (readLocksCounters.size() > 0) {
                if (readLocksCounters.size() != 1 || readLocksCounters.get(currentThread) == null) {
                    throw new IllegalMonitorStateException("can't be unlocked: it is locked to read by other threads");
                }
            }
            if (writeLocksCounter.decrementAndGet() == 0) {
                writeLockThread = null;
                notifyAll();
            }
        }
    }

    public void unlockRead() {
        final Thread currentThread = Thread.currentThread();
        synchronized (this) {
            if (writeLockThread != null && writeLockThread != currentThread) {
                throw new IllegalMonitorStateException("can't be unlocked: it is locked to write");
            }
            final AtomicLong counter = readLocksCounters.get(currentThread);
            if (counter == null) {
                throw new IllegalMonitorStateException("can't be unlocked: it is not loocked by this thread");
            }
            if (counter.decrementAndGet() == 0) {
                readLocksCounters.remove(currentThread);
                if (readLocksCounters.size() == 0) {
                    notifyAll();
                }
            }
        }
    }
}
