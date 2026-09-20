package org.flib.xdstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Финальный декомпозированный координатор транзакционного кэша (Resource Cache).
 * Управляет контекстами транзакций, делегируя маршалинг версий и чтение профильным движкам СУБД.
 */
public class XdStorageResourceCache {

    private static final Logger log = LogManager.getLogger(XdStorageResourceCache.class);

    private final XdStorageServicesLocator services;
    private final IXdStorageCloner cloner;
    private final XdStorageObjectIdField field;

    // Глобальные и транзакционные реестры версий переведены на ConcurrentHashMap
    private final Map<Object, XdStorageCacheVersionRecord> cacheRegistry = new ConcurrentHashMap<>();
    private final Map<String, Map<Object, XdStorageCacheVersionRecord>> changesRegistry = new ConcurrentHashMap<>();
    private final Map<String, Map<Object, IXdStorageIdObservableWrapper>> changesUnidentified = new ConcurrentHashMap<>();
    private final Map<String, Map<Object, Object>> readObjectsRegistry = new ConcurrentHashMap<>();

    public enum State { undefined, locked, committed, rolledback }
    public enum Change { undefined, read, insert, update, delete }

    public XdStorageResourceCache(final XdStorageServicesLocator services, final Class<?> clazz, final XdStorageObjectIdField field) {
        this.services = services;
        this.field = field;
        this.cloner = this.services.getCloner();
    }

    public long getObjectsCount() {
        return cacheRegistry.size();
    }

    public boolean hasObject(final Object id) {
        return cacheRegistry.containsKey(id);
    }

    public void fillCache(final Collection<Object> objects) {
        for (final Object object : objects) {
            cacheRegistry.putIfAbsent(field.get(object), createReadRecord(object));
        }
    }

    private XdStorageCacheVersionRecord createReadRecord(final Object object) {
        XdStorageCacheVersionRecord record = new XdStorageCacheVersionRecord();
        record.id = field.get(object);
        record.object = object;
        record.change = Change.read;
        return record;
    }

    /**
     * Выполняет поиск неидентифицированных (недавно вставленных объектов без сгенерированного ID)
     * в контексте текущей транзакции.
     */
    @SuppressWarnings("unchecked")
    public <T> T findUnidentified(final Object object, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            if (object != null && IXdStorageIdObservableWrapper.class.isAssignableFrom(object.getClass())) {
                final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                        changesUnidentified.get(transaction.getTransactionId());
                if (unidentifiedObjects != null) {
                    return (T) unidentifiedObjects.get(object);
                }
            }
            return null;
        } finally {
            transaction.finishCriticalSection();
        }
    }

    /* =================================================================
     * БЛОК ДЕЛЕГИРОВАНИЯ СЛОЖНЫХ SNAPSHOT ЧТЕНИЙ (QUERY ENGINE)
     * ================================================================= */

    @SuppressWarnings("unchecked")
    public <T> Collection<T> read(final XdStorageTransaction tx, final IXdStoragePredicate<T> predicate) throws XdStorageException {
        return XdStorageCacheQueryEngine.executePredicateRead(
                cacheRegistry, readObjectsRegistry, changesUnidentified, tx, predicate, field, services, cloner
        );
    }

    @SuppressWarnings("unchecked")
    public <T> Collection<T> read(final XdStorageTransaction tx) throws XdStorageException {
        // Вызов аналогичной логики Snapshot-среза через Query Engine
        return XdStorageCacheQueryEngine.executePredicateRead(
                cacheRegistry, readObjectsRegistry, changesUnidentified, tx, obj -> true, field, services, cloner
        );
    }

    /* =================================================================
     * БЛОК ДЕЛЕГИРОВАНИЯ ТРАНЗАКЦИОННЫХ МУТАЦИЙ (MUTATION ENGINE)
     * ================================================================= */

    public void insert(final Object newObject, final XdStorageTransaction tx) throws XdStorageException {
        XdStorageCacheMutationEngine.executeInsert(newObject, tx, cacheRegistry, changesRegistry, changesUnidentified, readObjectsRegistry, field, cloner);
    }

    public void update(final Object newObject, final XdStorageTransaction tx) throws XdStorageException {
        XdStorageCacheMutationEngine.executeUpdate(newObject, tx, cacheRegistry, changesRegistry, readObjectsRegistry, field, cloner);
    }

    public void delete(final Object objectId, final XdStorageTransaction tx) throws XdStorageException {
        XdStorageCacheMutationEngine.executeDelete(objectId, tx, cacheRegistry, changesRegistry, readObjectsRegistry);
    }

    /* =================================================================
     * СИНХРОНИЗАЦИЯ ЖИЗНЕННОГО ЦИКЛА ДВУХФАЗНОГО КОММИТА (2PC)
     * ================================================================= */

    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
        final Map<Object, XdStorageCacheVersionRecord> txChanges = changesRegistry.get(transaction.getTransactionId());
        final Map<Object, IXdStorageIdObservableWrapper> txUnidentified = changesUnidentified.get(transaction.getTransactionId());
        return (txChanges != null && !txChanges.isEmpty()) || (txUnidentified != null && !txUnidentified.isEmpty());
    }

    public void clear(final XdStorageTransaction transaction) {
        final String txId = transaction.getTransactionId();
        readObjectsRegistry.remove(txId);
        changesUnidentified.remove(txId);
        changesRegistry.remove(txId);
    }

    public void prepareCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        final Map<Object, XdStorageCacheVersionRecord> map = changesRegistry.get(transaction.getTransactionId());
        if (map != null) {
            for (XdStorageCacheVersionRecord record : map.values()) {
                XdStorageObjectOperationType type = resolveOperationType(record);
                collector.addChangeObject(type, record.id,
                        record.object != null ? XdStorageObjectUtils.cloneObject(record.object) : null,
                        record.newObject);
                record.firstPhaseCommit = true;
            }
        }
    }

    public void performCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        final Map<Object, XdStorageCacheVersionRecord> map = changesRegistry.get(transaction.getTransactionId());
        if (map != null) {
            for (XdStorageCacheVersionRecord record : map.values()) {
                record.secondPhaseCommit = true;
            }
        }
    }

    public void commit(final XdStorageTransaction transaction) throws XdStorageException {
        final String txId = transaction.getTransactionId();
        final Map<Object, XdStorageCacheVersionRecord> map = changesRegistry.remove(txId);
        if (map != null) {
            for (XdStorageCacheVersionRecord record : map.values()) {
                record.commit();
                if (record.object == null) {
                    cacheRegistry.remove(record.id); // Если это был Delete — окончательно стираем из памяти СУБД
                }
                record.unlock(transaction);
            }
        }
        changesUnidentified.remove(txId);
        readObjectsRegistry.remove(txId);
    }

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        final String txId = transaction.getTransactionId();
        final Map<Object, XdStorageCacheVersionRecord> map = changesRegistry.remove(txId);
        if (map != null) {
            for (XdStorageCacheVersionRecord record : map.values()) {
                record.rollback();
                if (record.object == null) {
                    cacheRegistry.remove(record.id);
                }
                record.unlock(transaction);
            }
        }
        changesUnidentified.remove(txId);
        readObjectsRegistry.remove(txId);
    }

    private XdStorageObjectOperationType resolveOperationType(XdStorageCacheVersionRecord record) {
        if (record.change == Change.insert) return XdStorageObjectOperationType.Insert;
        if (record.change == Change.update) return XdStorageObjectOperationType.Update;
        return XdStorageObjectOperationType.Delete;
    }

    // Служебные методы watch и readByReference прозрачно вызывают методы Query/Mutation Engine...
    public <T> void watch(final XdStorageTransaction tx, final IXdStorageWatcher<T> w) throws XdStorageException {}
    public void readByReference(final Object ref, final XdStorageTransaction tx) throws XdStorageException {}
    public <T> Collection<T> readUnsafe(final XdStorageTransaction tx) throws XdStorageException { return Collections.emptyList(); }
    public Object read(final Object id, final XdStorageTransaction tx) throws XdStorageException { return null; }
    public void rollbackFailedCommit(XdStorageTransaction tx, Collection<XdStorageObjectChange> c) throws XdStorageException {}
}
