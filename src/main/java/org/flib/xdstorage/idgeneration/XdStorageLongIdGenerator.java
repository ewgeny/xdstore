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

public class XdStorageLongIdGenerator implements IXdStorageIdGenerator {

    private final long PART_OF_IDENTIFIERS = 100;
    private final XdStorageServicesLocator services;
    private final Map<Class<?>, Lock> lockers = new ConcurrentHashMap<>();
    private final Map<Class<?>, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<Class<?>, Long> lastIdentifierOfPart = new ConcurrentHashMap<>();

    public XdStorageLongIdGenerator(final XdStorageServicesLocator provider) {
        this.services = provider;
    }

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
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

            // ИСПРАВЛЕНИЕ ПО ПОИНТУ Г (Prefetch Policy):
            long currentVal = counter.get();
            long limit = lastIdentifierOfPart.get(cl);

            if (currentVal + 1 >= limit) {
                takeNextPartOfIdentifiers(cl, transaction);
            }

            identifier = counter.incrementAndGet();

            if (identifier > lastIdentifierOfPart.get(cl)) {
                throw new XdStorageException("Критический сбой Hi-Lo буфера Long: сгенерированный ID вышел за пределы пачки!");
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

        final IXdStorageTransactionManager transactionsManager = services.getTransactionsManager();
        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final IXdStorageTransaction transaction = transactionsManager.beginTransaction(10 * 1000);
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

            transaction.commit();
        } catch (final XdStorageConnectionException e) {
            transaction.rollback();
            exception = new XdStorageException(e);
        } catch (final XdStorageException e) {
            transaction.rollback();
            exception = e;
        }

        if (exception != null) throw exception;

        counters.put(cl, new AtomicLong(newLastIdentifierOfPart - PART_OF_IDENTIFIERS));
        lastIdentifierOfPart.put(cl, newLastIdentifierOfPart);
    }

    private void takeNextPartOfIdentifiers(final Class<?> cl, final IXdStorageTransaction tx) throws XdStorageException {
        XdStorageException exception = null;
        final Class<?> clRecord = XdStorageLongIdCounterRecord.class;

        final IXdStorageTransactionManager transactionsManager = services.getTransactionsManager();
        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final Long newLastIdentifierOfPart = lastIdentifierOfPart.get(cl) + PART_OF_IDENTIFIERS;

        final IXdStorageTransaction transaction = transactionsManager.beginTransaction(10 * 1000);
        try {
            final XdStorageClassInfo clRecordInfo = XdStorageObjectUtils.getClassInfo(clRecord);
            final IXdStorageDaoResource resource = resourcesManager.lockStructureResource(clRecordInfo, (XdStorageTransaction) transaction);

            final XdStorageLongIdCounterRecord record = (XdStorageLongIdCounterRecord) resource.read(cl, (XdStorageTransaction) transaction);
            if (record != null) {
                record.setCounter(newLastIdentifierOfPart);
                resource.update(record, (XdStorageTransaction) transaction);
            }
            transaction.commit();
        } catch (final XdStorageConnectionException e) {
            transaction.rollback();
            exception = new XdStorageException(e);
        } catch (final XdStorageException e) {
            transaction.rollback();
            exception = e;
        }

        if (exception != null) throw exception;

        lastIdentifierOfPart.put(cl, newLastIdentifierOfPart);
    }
}
