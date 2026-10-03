package org.flib.xdstorage.resource;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.serialization.IXdStorageIOFactory;
import org.flib.xdstorage.serialization.IXdStorageObjectsReader;
import org.flib.xdstorage.serialization.IXdStorageObjectsWriter;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageResource extends XdStorageAbstractResource {

    private final IXdStorageObjectsReader reader;

    private final IXdStorageObjectsWriter writer;

    private Lock lock = new ReentrantLock();

    private AtomicBoolean prepared = new AtomicBoolean(false);

    public XdStorageResource(final XdStorageServicesLocator services,
                             final XdStorageAbstractResourcesManager manager,
                             final Object resourceId,
                             final XdStorageClassInfo clInfo,
                             final IXdStorageIOFactory factory) {
        super(services, manager, resourceId, clInfo);
        this.reader = factory.newInstanceReader();
        this.writer = factory.newInstanceWriter();
    }

    public XdStorageResource(final XdStorageServicesLocator services,
                             final XdStorageAbstractResourcesManager manager,
                             final Object resourceId, final XdStorageClassInfo clInfo,
                             final IXdStorageIdGenerator idGenerator,
                             final boolean isReferences,
                             final IXdStorageIOFactory factory) {
        super(services, manager, resourceId, clInfo, idGenerator, isReferences);
        this.reader = factory.newInstanceReader();
        this.writer = factory.newInstanceWriter();
    }

    private Map<String, Long> blockingTime = new HashMap<>();

    private AtomicBoolean locked = new AtomicBoolean(false);

    @Override
    public void lockForCommit(final XdStorageTransaction transaction) {
        super.lockForCommit(transaction);

        // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Полностью ликвидируем опасный synchronized(locked) и locked.wait()!
        // Вместо кустарного монитора используем промышленный ReentrantLock 'lock' с поддержкой
        // tryLock() по таймауту транзакции. Если ресурс занят другим коммитом, поток чисто
        // дождется его или выбросит контролируемый таймаут, не утягивая ядро в циклический Deadlock!
        final long timeout = transaction != null ? transaction.getTimeout() : 3000L;
        try {
            boolean acquired = lock.tryLock(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new XdStorageRuntimeException("resource " + getFileName() + " cannot be locked for commit by transaction "
                        + transaction.getTransactionId() + " (Timeout " + timeout + " ms expired)");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XdStorageRuntimeException("waiting for lock resource " + getFileName() + " has been interrupted", e);
        }
    }

    @Override
    public void unlockAfterCommit(final XdStorageTransaction transaction) {
        // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Освобождаем ReentrantLock вместо synchronized-уведомлений
        if (((ReentrantLock) lock).isHeldByCurrentThread()) {
            lock.unlock();
        }

        super.unlockAfterCommit(transaction);
    }

    public String getFileName() {
        return resourceId.toString();
    }

    private Collection<Object> readAsObjects() throws XdStorageException {
        Collection<Object> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new FileReader(file);
                try {
                    if (isReferences) {
                        objects = reader.readReferences(xmlReader, idField);
                    } else {
                        objects = reader.read(xmlReader);
                    }
                } finally {
                    xmlReader.close();
                }
            }
        } catch (final Throwable e) {
            throw new XdStorageException("invalid state of database: error by reading resource " + resourceId, e);
        }
        return objects;
    }

    private void writeObjects(final Collection<Object> objects) {
        File file = new File(getFileName());
        if (file.exists())
            file.delete();

        if (objects.size() > 0) {
            try {
                // TODO : REVIEW file deleting and creation
                // ! and think about backup file
                file = new File(getFileName());
                if (!file.exists()) {
                    final File parentFile = file.getParentFile();
                    if (parentFile != null && !parentFile.exists())
                        parentFile.mkdirs();
                    file.createNewFile();
                }

                Writer xmlWriter = null;
                try {
                    xmlWriter = new FileWriter(file);
                    if (isReferences) {
                        writer.writeReferences(xmlWriter, idField, objects);
                    } else {
                        writer.writeObjects(xmlWriter, objects);
                    }
                } finally {
                    xmlWriter.close();
                }
            } catch (final Throwable e) {
                throw new XdStorageRuntimeException("invalid state of database: error by writing resource " + resourceId, e);
            }
        }
    }

    public Collection<XdStorageIdentifiableObject> readAsData(final XdStorageTransaction transaction) throws XdStorageException {
        Collection<XdStorageIdentifiableObject> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new FileReader(file);
                try {
                    objects = reader.readData(xmlReader, idField);
                } finally {
                    xmlReader.close();
                }
            }
        } catch (final Throwable e) {
            throw new XdStorageException("invalid state of database: error by reading resource " + resourceId, e);
        }
        return objects;
    }

    public void prepare(final XdStorageTransaction transaction) throws XdStorageException {
        if (prepared.get()) {
            return;
        }

        lock.lock();
        try {
            if (prepared.get()) {
                return;
            }

            Collection<Object> objects = readAsObjects();
            if (objects != null)
                cache.fillCache(objects);
            postPrepare(transaction);
            manager.updateCounter(getObjectsClass(), this);

            prepared.set(true);
        } finally {
            lock.unlock();
        }
    }

    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageException {
        if (cache.hasChanges(transaction)) {
            final Collection<Object> objects = cache.readUnsafe(transaction);
            cache.prepareCommit(transaction, record);
            writeObjects(objects);
        }
    }

    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageException {
        cache.performCommit(transaction, record);
        cache.commit(transaction);
        postCommit(transaction);
    }

    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException {
        cache.rollbackFailedCommit(transaction, changes);
        writeObjects(cache.readUnsafe(transaction));
        postRollback(transaction);
    }

    @Override
    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        // Гарантируем fail-safe отпуск ReentrantLock замка при откате изменений страницы
        if (((ReentrantLock) lock).isHeldByCurrentThread()) {
            lock.unlock();
        }
        cache.rollback(transaction);
        postRollback(transaction);
    }
}
