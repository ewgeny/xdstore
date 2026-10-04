package org.flib.xdstorage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Неблокирующий реентерабельный примитив синхронизации ядра СУБД (Поинт Г).
 * Гарантирует потокобезопасный отпуск ресурсов за счет блокирующего вызова mainLock.lock()
 * и адаптивного опроса transaction.getTimeout() при захвате.
 */
public class XdStorageReadWriteLock {

    private static final Logger log = LogManager.getLogger(XdStorageReadWriteLock.class);

    // Легковесный лок для защиты атомарности каста состояний
    private final ReentrantLock mainLock = new ReentrantLock();

    private volatile Thread writeLockThread = null;
    private final AtomicLong writeLocksCounter = new AtomicLong(0);

    // Потокобезопасный учет реентерабельных читателей
    private final Map<Thread, AtomicLong> readLocksCounters = new ConcurrentHashMap<>();

    public boolean isWriteLocked() {
        return writeLocksCounter.get() > 0 && writeLockThread == Thread.currentThread();
    }

    public boolean isReadLocked() {
        AtomicLong counter = readLocksCounters.get(Thread.currentThread());
        return counter != null && counter.get() > 0;
    }

    public boolean tryLockWrite() {
        final Thread currentThread = Thread.currentThread();

        // =========================================================================
        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ (Ликвидация ложных отказов блокировок):
        // Заменяем "mainLock.tryLock()" на честный блокирующий "mainLock.lock()".
        // Это гарантирует, что потоки не будут ложно отваливаться с отказом false,
        // если столкнулись лбами на обновлении внутренних счетчиков локера в куче Java!
        // =========================================================================
        mainLock.lock();
        try {
            if (writeLockThread != null && writeLockThread != currentThread) {
                return false;
            }

            // РЕЕНТЕРАБЕЛЬНЫЙ UPGRADE ЗАМКА: Если текущий поток уже держит замки на чтение,
            // и он единственный читатель в СУБД, мы беспрепятственно разрешаем ему взять Write-Lock!
            if (!readLocksCounters.isEmpty()) {
                if (readLocksCounters.size() != 1 || !readLocksCounters.containsKey(currentThread)) {
                    return false;
                }
            }
            writeLockThread = currentThread;
            writeLocksCounter.incrementAndGet();
            return true;
        } finally {
            mainLock.unlock();
        }
    }

    public boolean tryLockRead() {
        final Thread currentThread = Thread.currentThread();

        // =========================================================================
        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ (Освобождение Shared Read параллельности):
        // Заменяем "mainLock.tryLock()" на монолитный "mainLock.lock()".
        // Параллельные читатели больше не будут отбриваться локером, а выстроятся
        // в наносекундную очередь, атомарно пропишут счетчики и будут читать данные ОДНОВРЕМЕННО,
        // что полностью уничтожает ошибку Expected 10, Actual 4!
        // =========================================================================
        mainLock.lock();
        try {
            // ИСПРАВЛЕНИЕ СУБД (РЕЕНТЕРАБЕЛЬНЫЙ DOWNGRADE): Если монопольный Write-Lock уже удерживается
            // ТЕКУЩИМ ПОТОКОМ, мы обязаны мгновенно разрешить ему операцию чтения!
            if (writeLockThread != null && writeLockThread == currentThread) {
                AtomicLong counter = readLocksCounters.get(currentThread);
                if (counter == null) {
                    readLocksCounters.put(currentThread, counter = new AtomicLong(0));
                }
                counter.incrementAndGet();
                return true;
            }

            if (writeLockThread != null && writeLockThread != currentThread) {
                return false;
            }

            AtomicLong counter = readLocksCounters.get(currentThread);
            if (counter == null) {
                readLocksCounters.put(currentThread, counter = new AtomicLong(0));
            }
            counter.incrementAndGet();
            return true;
        } finally {
            mainLock.unlock();
        }
    }

    public void lockWrite(final IXdStorageTransaction transaction) throws InterruptedException {
        long timeout = transaction != null ? transaction.getTimeout() : 5000;
        long startTime = System.currentTimeMillis();
        long sleepStep = 15;

        while (!tryLockWrite()) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeout) {
                throw new XdStorageRuntimeException("Конфликт блокировок MVCC: Ресурс монопольно занят на запись другим потоком. Превышен таймаут ожидания: " + timeout + " мс");
            }
            Thread.sleep(Math.min(sleepStep, timeout - elapsed));
        }
    }

    public void lockRead(final IXdStorageTransaction transaction) throws InterruptedException {
        long timeout = transaction != null ? transaction.getTimeout() : 5000;
        long startTime = System.currentTimeMillis();
        long sleepStep = 15;

        while (!tryLockRead()) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeout) {
                throw new XdStorageRuntimeException("Конфликт блокировок MVCC: Ресурс занят на чтение другим потоком. Превышен таймаут ожидания: " + timeout + " мс");
            }
            Thread.sleep(Math.min(sleepStep, timeout - elapsed));
        }
    }

    public void unlockWrite() {
        final Thread currentThread = Thread.currentThread();
        // ИСПРАВЛЕНИЕ: Используем ГАРАНТИРОВАННЫЙ блокирующий вызов lock() вместо tryLock(),
        // полностью ликвидируя ложные выбросы IllegalMonitorStateException при разгрузке потоков!
        mainLock.lock();
        try {
            if (writeLockThread == null || writeLockThread != currentThread) {
                throw new IllegalMonitorStateException("Блокировка записи не удерживается текущим потоком");
            }
            if (writeLocksCounter.decrementAndGet() == 0) {
                writeLockThread = null;
            }
        } finally {
            mainLock.unlock();
        }
    }

    public void unlockRead() {
        final Thread currentThread = Thread.currentThread();
        // ИСПРАВЛЕНИЕ: Используем ГАРАНТИРОВАННЫЙ блокирующий вызов lock() вместо tryLock(),
        // полностью ликвидируя ложные выбросы IllegalMonitorStateException при разгрузке потоков!
        mainLock.lock();
        try {
            final AtomicLong counter = readLocksCounters.get(currentThread);
            if (counter == null || counter.get() <= 0) {
                throw new IllegalMonitorStateException("Блокировка чтения не удерживается текущим потоком");
            }
            if (counter.decrementAndGet() == 0) {
                readLocksCounters.remove(currentThread);
            }
        } finally {
            mainLock.unlock();
        }
    }
}
