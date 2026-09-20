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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageIntegerIdGenerator implements IXdStorageIdGenerator {

    private final int PART_OF_IDENTIFIERS = 100;

    private final XdStorageServicesLocator services;

    private final Map<Class<?>, Lock> lockers = new ConcurrentHashMap<>();

    private Map<Class<?>, AtomicInteger> counters = new ConcurrentHashMap<>();

    private Map<Class<?>, Integer> lastIdentifierOfPart = new ConcurrentHashMap<>();

    public XdStorageIntegerIdGenerator(final XdStorageServicesLocator provider) {
        this.services = provider;
    }

    @Override
    public Object generate(final Class<?> cl, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        Lock locker = lockers.get(cl);
        if (locker == null) {
            lockers.putIfAbsent(cl, new ReentrantLock());
            locker = lockers.get(cl);
        }

        int identifier;
        try {
            locker.lock();

            AtomicInteger counter = counters.get(cl);
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

        Integer newLastIdentifierOfPart = null;

        final Class<?> clRecord = XdStorageIntegerIdCounterRecord.class;

        final IXdStorageTransactionManager transactionsManager = services.getTransactionsManager();
        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final IXdStorageTransaction transaction = transactionsManager.beginTransaction(10 * 1000);
        try {
            final XdStorageClassInfo clRecordInfo = XdStorageObjectUtils.getClassInfo(clRecord);
            final IXdStorageDaoResource resource = resourcesManager.lockStructureResource(clRecordInfo, (XdStorageTransaction) transaction);

            XdStorageIntegerIdCounterRecord record = (XdStorageIntegerIdCounterRecord) resource.read(cl, (XdStorageTransaction) transaction);
            if (record == null) {
                newLastIdentifierOfPart = PART_OF_IDENTIFIERS;

                record = new XdStorageIntegerIdCounterRecord();
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

        counters.put(cl, new AtomicInteger(newLastIdentifierOfPart - PART_OF_IDENTIFIERS));
        lastIdentifierOfPart.put(cl, newLastIdentifierOfPart);
    }

    private void takeNextPartOfIdentifiers(final Class<?> cl, final IXdStorageTransaction tx) throws XdStorageException {
        XdStorageException exception = null;

        final Class<?> clRecord = XdStorageIntegerIdCounterRecord.class;

        final IXdStorageTransactionManager transactionsManager = services.getTransactionsManager();
        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final Integer newLastIdentifierOfPart = lastIdentifierOfPart.get(cl) + PART_OF_IDENTIFIERS;

        final IXdStorageTransaction transaction = transactionsManager.beginTransaction(10 * 1000);
        try {
            final XdStorageClassInfo clRecordInfo = XdStorageObjectUtils.getClassInfo(clRecord);
            final IXdStorageDaoResource resource = resourcesManager.lockStructureResource(clRecordInfo, (XdStorageTransaction) transaction);

            final XdStorageIntegerIdCounterRecord record = (XdStorageIntegerIdCounterRecord) resource.read(cl, (XdStorageTransaction) transaction);
            record.setCounter(newLastIdentifierOfPart);
            resource.update(record, (XdStorageTransaction) transaction);

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
