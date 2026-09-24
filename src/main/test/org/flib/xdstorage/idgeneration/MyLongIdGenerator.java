package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.IXdStorageTransactionManager;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MyLongIdGenerator implements IXdStorageIdGenerator {

    private final long PART_OF_IDENTIFIERS = 100;

    private final XdStorageServicesLocator services;

    private final Map<Class<?>, Lock> lockers = new ConcurrentHashMap<>();

    private Map<Class<?>, AtomicLong> counters = new ConcurrentHashMap<>();

    private Map<Class<?>, Long> lastIdentifierOfPart = new ConcurrentHashMap<>();

    public MyLongIdGenerator(final XdStorageServicesLocator provider) {
        this.services = provider; // Имя должно совпадать с полем!
    }

    @Override
    public Object generate(Class<?> cl, IXdStorage storage, IXdStorageTransaction transaction) throws XdStorageException {
        Lock locker = lockers.get(cl);
        if (locker == null) {
            lockers.putIfAbsent(cl, new ReentrantLock());
            locker = lockers.get(cl);
        }

        long identifier;
        try {
            locker.lock();

            AtomicLong counter = counters.get(cl);
            if (counter == null) {
                initIdentifiers(cl, transaction);
                counter = counters.get(cl);
            }

            if ((identifier = counter.incrementAndGet()) == lastIdentifierOfPart.get(cl)) {
                takeNextPartOfIdentifiers(cl, transaction);
            }
        } finally {
            locker.unlock();
        }
        return identifier;
    }

    private void initIdentifiers(final Class<?> cl, final IXdStorageTransaction tx) throws XdStorageException {
        XdStorageException exception = null;
        final Class<?> clRecord = XdStorageLongIdCounterRecord.class;
        Long newLastIdentifierOfPart = null;

        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        // ИСПРАВЛЕНИЕ: Используем переданную транзакцию tx текущего потока,
        // чтобы избежать Self-Concurrent-Modification блокировок в рамках одного потока!
        final IXdStorageTransaction transaction = (tx != null) ? tx : services.getTransactionsManager().beginTransaction(10 * 1000);

        try {
            final XdStorageClassInfo clRecordInfo = XdStorageObjectUtils.getClassInfo(clRecord);
            final IXdStorageDaoResource resource = resourcesManager.lockStructureResource(clRecordInfo, (XdStorageTransaction) transaction);

            XdStorageLongIdCounterRecord record = (XdStorageLongIdCounterRecord) resource.read(cl, (XdStorageTransaction) transaction);
            if (record == null) {
                newLastIdentifierOfPart = PART_OF_IDENTIFIERS;
                record = new XdStorageLongIdCounterRecord();
                record.setCl(cl);
                record.setCounter(newLastIdentifierOfPart);
                resource.insert(record, (XdStorageTransaction) transaction);
            } else {
                newLastIdentifierOfPart = record.getCounter() + PART_OF_IDENTIFIERS;
                record.setCounter(newLastIdentifierOfPart);
                resource.update(record, (XdStorageTransaction) transaction);
            }

            // Коммитим только если транзакция автономная (создана локально)
            if (tx == null) {
                transaction.commit();
            }
        } catch (final Throwable e) {
            if (tx == null) {
                transaction.rollback();
            }
            exception = new XdStorageException(e);
        }

        if (exception != null) throw exception;

        counters.put(cl, new java.util.concurrent.atomic.AtomicLong(newLastIdentifierOfPart - PART_OF_IDENTIFIERS));
        lastIdentifierOfPart.put(cl, newLastIdentifierOfPart);
    }

    private void takeNextPartOfIdentifiers(final Class<?> cl, final IXdStorageTransaction tx) throws XdStorageException {
        XdStorageException exception = null;
        final Class<?> clRecord = XdStorageLongIdCounterRecord.class;
        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final Long newLastIdentifierOfPart = lastIdentifierOfPart.get(cl) + PART_OF_IDENTIFIERS;

        // ИСПРАВЛЕНИЕ: Интегрируемся в текущий транзакционный контекст нити
        final IXdStorageTransaction transaction = (tx != null) ? tx : services.getTransactionsManager().beginTransaction(10 * 1000);

        try {
            final XdStorageClassInfo clRecordInfo = XdStorageObjectUtils.getClassInfo(clRecord);
            final IXdStorageDaoResource resource = resourcesManager.lockStructureResource(clRecordInfo, (XdStorageTransaction) transaction);

            final XdStorageLongIdCounterRecord record = (XdStorageLongIdCounterRecord) resource.read(cl, (XdStorageTransaction) transaction);
            if (record != null) {
                record.setCounter(newLastIdentifierOfPart);
                resource.update(record, (XdStorageTransaction) transaction);
            } else {
                // Страховочный фолбэк на случай пустой базы данных
                XdStorageLongIdCounterRecord newRecord = new XdStorageLongIdCounterRecord();
                newRecord.setCl(cl);
                newRecord.setCounter(newLastIdentifierOfPart);
                resource.insert(newRecord, (XdStorageTransaction) transaction);
            }

            if (tx == null) {
                transaction.commit();
            }
        } catch (final Throwable e) {
            if (tx == null) {
                transaction.rollback();
            }
            exception = new XdStorageException(e);
        }

        if (exception != null) throw exception;

        lastIdentifierOfPart.put(cl, newLastIdentifierOfPart);
    }
}
