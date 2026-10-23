package io.github.xdstorage.index.hash;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import io.github.xdstorage.idgeneration.IXdStorageIdGenerator;
import io.github.xdstorage.idgeneration.XdStorageIdGeneratorType;
import io.github.xdstorage.object.XdStorageIdentifiableObject;
import io.github.xdstorage.observing.XdStorageObserverService;
import io.github.xdstorage.resource.XdStorageAbstractResourcesManager;
import io.github.xdstorage.resource.XdStorageObjectChange;
import io.github.xdstorage.serialization.IXdStorageIOFactory;
import io.github.xdstorage.serialization.IXdStorageObjectsReader;
import io.github.xdstorage.serialization.IXdStorageObjectsWriter;
import io.github.xdstorage.services.XdStorageServicesLocator;
import io.github.xdstorage.transaction.XdStorageTransaction;
import io.github.xdstorage.transaction.XdStorageTransactionResourceChanges;
import io.github.xdstorage.utils.XdStorageAtomicFile;
import io.github.xdstorage.utils.XdStorageClassInfo;

import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageHashIndexResource extends XdStorageAbstractHashIndexResource {

    private static final Logger log = LoggerFactory.getLogger(XdStorageHashIndexResource.class);

    private final IXdStorageObjectsReader reader;

    private final IXdStorageObjectsWriter writer;

    private final AtomicBoolean prepared = new AtomicBoolean(false);

    private final Lock lock = new ReentrantLock();

    public XdStorageHashIndexResource(final XdStorageServicesLocator services,
                                      final XdStorageAbstractResourcesManager manager,
                                      final Object resourceId, final String indexName, final XdStorageClassInfo clInfo,
                                      final IXdStorageIOFactory factory, final int fragmentSize) {
        super(services, manager, resourceId, indexName, clInfo, fragmentSize);
        this.reader = factory.newInstanceReader();
        this.writer = factory.newInstanceWriter();
    }

    public XdStorageHashIndexResource(final XdStorageServicesLocator services,
                                      final XdStorageAbstractResourcesManager manager,
                                      final Object resourceId, final String indexName, final XdStorageClassInfo clInfo,
                                      final IXdStorageIOFactory factory, final IXdStorageIdGenerator idGenerator, final int fragmentSize) {
        super(services, manager, resourceId, indexName, clInfo, idGenerator, fragmentSize);
        this.reader = factory.newInstanceReader();
        this.writer = factory.newInstanceWriter();
    }

    private Map<String, Long> blockingTime = new HashMap<>();

    private AtomicBoolean locked = new AtomicBoolean(false);

    @Override
    public void lockForCommit(final XdStorageTransaction transaction) {
        super.lockForCommit(transaction);

        // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Ликвидируем synchronized(locked) и locked.wait() в Хеш-индексах!
        // Задействуем ReentrantLock 'lock' с поддержкой tryLock() по таймауту транзакции.
        final long timeout = transaction != null ? transaction.getTimeout() : 3000L;
        try {
            boolean acquired = lock.tryLock(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new XdStorageRuntimeException("Hash Index Resource " + getFileName() + " cannot be locked for commit by transaction "
                        + transaction.getTransactionId() + " (Timeout " + timeout + " ms expired)");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XdStorageRuntimeException("waiting for lock hash index resource " + getFileName() + " has been interrupted", e);
        }
    }

    @Override
    public void unlockAfterCommit(final XdStorageTransaction transaction) {
        // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Освобождаем ReentrantLock хеш-индекса
        if (((ReentrantLock) lock).isHeldByCurrentThread()) {
            lock.unlock();
        }
        super.unlockAfterCommit(transaction);
    }

    public String getFileName() {
        return resourceId.toString();
    }

    private Collection<Object> readAsObjects() {
        Collection<Object> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new java.io.BufferedReader(new FileReader(file), 1 << 16);
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
            throw new XdStorageRuntimeException("invalid state of database: error by reading resource " + resourceId, e);
        }
        return objects;
    }

    private void writeObjects(final Collection<Object> objects) {
        if (objects.size() == 0) {
            // пустой ресурс = файла нет (прежняя семантика)
            final File file = new File(getFileName());
            if (file.exists())
                file.delete();
            return;
        }

        try {
            // crash-safe: пишем во временный файл-соседа и атомарно подменяем ресурс,
            // чтобы сбой посреди коммита не мог обрезать существующий файл ресурса
            XdStorageAtomicFile.writeReplacing(getFileName(), services.getStorageConfig().isDurableWrites(), out -> {
                if (isReferences) {
                    writer.writeReferences(out, idField, objects);
                } else {
                    writer.writeObjects(out, objects);
                }
            });
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("invalid state of database: error by writing resource " + resourceId, e);
        }
    }

    public Collection<XdStorageIdentifiableObject> readAsData(XdStorageTransaction transaction) {
        Collection<XdStorageIdentifiableObject> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new java.io.BufferedReader(new FileReader(file), 1 << 16);
                try {
                    objects = reader.readData(xmlReader, idField);
                } finally {
                    xmlReader.close();
                }
            }
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("invalid state of database: error by reading resource " + resourceId, e);
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

            if (objects != null) {
                // BUG-C (docs/tasks/2.2.0/03): the YAML round-trip does not know the ENTITY's
                // id-field type, so reopened record keys arrive as raw strings/numbers and a
                // lookup by the application's real id (Long 9000 vs stored "9000") misses —
                // every id lookup silently returns null after a reopen for numeric id types.
                // Restore each record key to the id-field type before filling the cache.
                for (final Object object : objects) {
                    if (object instanceof XdStorageHashIndexRecord) {
                        ((XdStorageHashIndexRecord) object).setObjectId(
                                restoreKeyValueType(((XdStorageHashIndexRecord) object).getObjectId()));
                    }
                }
                cache.fillCache(objects);
            }
            postPrepare(transaction);

            prepared.set(true);
        } finally {
            lock.unlock();
        }
    }

    /**
     * BUG-C: restores a record key to the entity id-field type after the YAML round-trip.
     * Values that are already of the target type pass through; strings/numbers are parsed
     * via the engine's simple-type conversion; anything unparseable is returned as-is (the
     * lookup then misses loudly instead of corrupting the cache with a wrong-typed key).
     */
    private Object restoreKeyValueType(final Object key) {
        // BUG-C: the authority is the ENTITY's id-field type — this resource's own
        // `clInfo`/`idField` describe the RECORD class (XdStorageHashIndexRecord.objectId,
        // declared Object), which would silently restore nothing
        final Object restored = io.github.xdstorage.utils.XdStorageKeyTypeRestore.restore(
                key, objectClInfo.getIdField().field.getType());
        if (restored != key) {
            log.debug("restored id-index key type: {} -> {}", key, restored);
        }
        return restored;
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

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        // Гарантируем fail-safe отпуск ReentrantLock замка хеш-индекса при аварийном откате
        if (((ReentrantLock) lock).isHeldByCurrentThread()) {
            lock.unlock();
        }
        cache.rollback(transaction);
        postRollback(transaction);
    }

    @Override
    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        Object objectId = objectIdField.get(object);
        // BUG-D (docs/tasks/2.1.0/14): UUID_GENERATOR generates here too — the mux
        // dispatches by id type and validates UUID_GENERATOR to String id fields.
        if (objectId == null &&
                (objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                        || objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.UUID_GENERATOR
                        || objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                && idGenerator != null) {
            objectIdField.set(XdStorageObserverService.getObservableWrapper(object), objectId = idGenerator.generate(object.getClass(), services.getStorage(), transaction));
        }
        final Object resourceId = index.getResourceId(objectId);
        if (resourceId != null) {
            throw new XdStorageException("object of class " + object.getClass() + " with idgeneration " + objectId + " is exists");
        }
        super.insert(object, transaction);
    }
}
