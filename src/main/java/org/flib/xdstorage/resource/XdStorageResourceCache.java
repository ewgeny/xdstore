package org.flib.xdstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageResourceCache {

    private static final Logger log = LogManager.getLogger(XdStorageResourceCache.class);

    private final XdStorageServicesLocator services;

    private final IXdStorageCloner cloner;

    private final XdStorageObjectIdField field;

    private final Map<Object, CacheRecord> cache = new ConcurrentHashMap<>();

    private final Map<String, Map<Object, CacheRecord>> changes = new ConcurrentHashMap<>();

    private final Map<String, Map<Object, IXdStorageIdObservableWrapper>> changesUnidentifiedObjectsByTransaction = new ConcurrentHashMap<>();

    private final Map<String, Map<Object, ObjectChange>> readObjectsByTransaction = new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    private AtomicBoolean isWrappableClass;

    private Class<?> clazz;

    public XdStorageResourceCache(final XdStorageServicesLocator services, final Class<?> clazz, final XdStorageObjectIdField field) {
        this.services = services;
        this.field = field;
        this.clazz = clazz;
        this.cloner = this.services.getCloner();
    }

    public long getObjectsCount() {
        return cache.size();
    }

    public void fillCache(final Collection<Object> objects) {
        for (final Object object : objects) {
            cache.putIfAbsent(field.get(object), createReadRecord(object));
        }
    }

    public <T> void fillCacheOnlyIfNotExists(final Collection<T> objects) {
        for (final Object object : objects) {
            cache.putIfAbsent(field.get(object), createReadRecord(object));
        }
    }

    public <T> void fillAndCloneCacheOnlyIfNotExists(final List<T> objects, final Collection<T> result, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            fillAndCloneCacheOnlyIfNotExistsInternal(objects, result, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private <T> void fillAndCloneCacheOnlyIfNotExistsInternal(final List<T> objects, final Collection<T> result, final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> tmpReadObjects = readObjectsByTransaction.get(transactionId);
        if (tmpReadObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            tmpReadObjects = readObjectsByTransaction.get(transactionId);
        }

        for (final T object : objects) {
            final Object objectId = field.get(object);
            final Object clonedObject = cloner.cloneAndWrap(object, services.getStorage(), transaction);
            tmpReadObjects.putIfAbsent(objectId, new ObjectChange(clonedObject, Change.read, System.nanoTime()));
            result.add((T) tmpReadObjects.get(objectId).object);
            cache.putIfAbsent(field.get(object), createReadRecord(object));
        }
    }

    private boolean isWrappableObject(final Object object) {
        if (isWrappableClass == null) {
            lock.lock();
            try {
                if(isWrappableClass == null) {
                    isWrappableClass = new AtomicBoolean(XdStorageObjectUtils.isWrappableObject(object));
                }
            } finally {
                lock.unlock();
            }
        }
        return isWrappableClass.get();
    }

    public boolean hasObject(final Object id) {
        return cache.containsKey(id);
    }

    public <T> T findUnidentified(final Object object, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            return findUnidentifiedInternal(object, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private <T> T findUnidentifiedInternal(final Object object, final XdStorageTransaction transaction) {
        if (IXdStorageIdObservableWrapper.class.isAssignableFrom(object.getClass())) {
            final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                    changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
            if (unidentifiedObjects != null) {

                return (T) unidentifiedObjects.get(object);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            return readInternal(transaction, predicate);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private <T> Collection<T> readInternal(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException {
        final XdStorageException[] exceptions = new XdStorageException[]{null};
        final AtomicBoolean hasError = new AtomicBoolean(false);

        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> tmpReadObjects = readObjectsByTransaction.get(transactionId);
        if (tmpReadObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            tmpReadObjects = readObjectsByTransaction.get(transactionId);
        }
        final Map<Object, ObjectChange> readObjects = tmpReadObjects;

        final List<T> result = Collections.synchronizedList(new LinkedList<>());
        cache.values().parallelStream().forEach(record -> {
            if (hasError.get()) {
                return;
            }

            Object object = null;
            if (record.isUpdateChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                } else {
                    object = record.getObject();
                }
            } else if (record.isInsertChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                }
            } else if (record.isDeleteChange()) {
                if (!record.isCommitedState() && !record.isChangedByTransaction(transaction)) {
                    object = record.getObject();
                }
            } else {
                object = record.getObject();
            }

            try {
                if (object != null) {
                    final Object objectId = field.get(object);
                    Object wrappedObject, objectToReturn = null;
                    if (readObjects.containsKey(objectId)) {
                        wrappedObject = XdStorageObjectUtils.wrapAsUnmodifiableObject(objectToReturn = readObjects.get(objectId).object, services.getStorage(), transaction);
                    } else {
                        wrappedObject = XdStorageObjectUtils.wrapAsUnmodifiableObject(object, services.getStorage(), transaction);
                    }

                    if(predicate.passed((T) wrappedObject)) {
                        if (objectToReturn == null) {
                            readObjects.putIfAbsent(objectId, new ObjectChange(cloner.cloneAndWrap(object, services.getStorage(), transaction), Change.read, System.nanoTime()));
                            objectToReturn = readObjects.get(objectId).object;
                        }
                        result.add((T) objectToReturn);
                    }
                }
            } catch (final Throwable e) {
                if (hasError.get()) {
                    return;
                }
                hasError.set(true);
                exceptions[0] = new XdStorageException(e);
            }
        });

        if (exceptions[0] != null)
            throw exceptions[0];

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
        if (unidentifiedObjects != null) {
            unidentifiedObjects.values().parallelStream().forEach(wrapper -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    if (predicate.passed((T) wrapper)) {
                        result.add((T) wrapper);
                    }
                } catch (final Throwable e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    exceptions[0] = new XdStorageException(e);
                }
            });

            if (exceptions[0] != null)
                throw exceptions[0];
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            watchInternal(transaction, watcher);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private <T> void watchInternal(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException {
        final XdStorageException[] exceptions = new XdStorageException[]{null};
        final AtomicBoolean hasError = new AtomicBoolean(false);

        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> tmpReadObjects = readObjectsByTransaction.get(transactionId);
        if (tmpReadObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            tmpReadObjects = readObjectsByTransaction.get(transactionId);
        }
        final Map<Object, ObjectChange> readObjects = tmpReadObjects;

        cache.values().stream().forEach(record -> {
            if (hasError.get()) {
                return;
            }

            Object object = null;
            if (record.isUpdateChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                } else {
                    object = record.getObject();
                }
            } else if (record.isInsertChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                }
            } else if (record.isDeleteChange()) {
                if (!record.isCommitedState() && !record.isChangedByTransaction(transaction)) {
                    object = record.getObject();
                }
            } else {
                object = record.getObject();
            }

            try {
                if (object != null) {
                    final Object objectId = field.get(object);
                    final Object objectToWrap;
                    if (readObjects.containsKey(objectId)) {
                        objectToWrap = readObjects.get(objectId).object;
                    } else if (isWrappableObject(object)) {
                        objectToWrap = object;
                    } else {
                        objectToWrap = XdStorageObjectUtils.cloneObject(object);
                    }
                    watcher.watch((T) XdStorageObjectUtils.wrapAsUnmodifiableObject(objectToWrap, services.getStorage(), transaction));
                }
            } catch (final Throwable e) {
                if (hasError.get()) {
                    return;
                }
                hasError.set(true);
                exceptions[0] = new XdStorageException(e);
            }
        });

        if (exceptions[0] != null)
            throw exceptions[0];

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects = changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
        if (unidentifiedObjects != null) {
            unidentifiedObjects.values().stream().forEach(wrapper -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    watcher.watch((T) wrapper);
                } catch (final Throwable e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    exceptions[0] = new XdStorageException(e);
                }
            });

            if (exceptions[0] != null)
                throw exceptions[0];
        }
    }

    public <T> Collection<T> read(final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            return readInternal(transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private <T> Collection<T> readInternal(final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
        if (readObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            readObjects = readObjectsByTransaction.get(transactionId);
        }

        final List<T> result = Collections.synchronizedList(new ArrayList<>());
        for (final CacheRecord record : cache.values()) {

            Object object = null;
            if (record.isUpdateChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                } else {
                    object = record.getObject();
                }
            } else if (record.isInsertChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                }
            } else if (record.isDeleteChange()) {
                if (!record.isCommitedState() && !record.isChangedByTransaction(transaction)) {
                    object = record.getObject();
                }
            } else {
                object = record.getObject();
            }

            if (object != null) {
                final Object objectId = field.get(object);
                if (!readObjects.containsKey(objectId)) {
                    readObjects.putIfAbsent(objectId, new ObjectChange(cloner.cloneAndWrap(object, services.getStorage(), transaction), Change.read, System.nanoTime()));
                }
                result.add((T) readObjects.get(objectId).object);
            }
        }
        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects = changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
        if (unidentifiedObjects != null) {
            for (final IXdStorageIdObservableWrapper wrapper : unidentifiedObjects.values()) {
                result.add((T) wrapper);
            }
        }
        return result;
    }

    public <T> Collection<T> readUnsafe(final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            return readUnsafeInternal(transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private <T> Collection<T> readUnsafeInternal(final XdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
        if (readObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            readObjects = readObjectsByTransaction.get(transactionId);
        }

        final List<T> result = Collections.synchronizedList(new ArrayList<>());
        for (final CacheRecord record : cache.values()) {

            Object object = null;
            if (record.isUpdateChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                } else {
                    object = record.getObject();
                }
            } else if (record.isInsertChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                }
            } else if (record.isDeleteChange()) {
                if (!record.isCommitedState() && !record.isChangedByTransaction(transaction)) {
                    object = record.getObject();
                }
            } else {
                object = record.getObject();
            }

            if (object != null) {
                final Object objectId = field.get(object);
                final ObjectChange readObject = readObjects.get(objectId);
                if (readObject != null && readObject.change != Change.read) {
                    result.add((T) readObject.object);
                } else {
                    result.add((T) object);
                }
            }
        }
        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects = changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
        if (unidentifiedObjects != null) {
            for (final IXdStorageIdObservableWrapper wrapper : unidentifiedObjects.values()) {
                result.add((T) wrapper);
            }
        }
        return result;
    }

    public Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            return readInternal(id, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private Object readInternal(final Object id, final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
        if (readObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            readObjects = readObjectsByTransaction.get(transactionId);
        } else if (readObjects.containsKey(id)) {
            return readObjects.get(id).object;
        }

        CacheRecord record = cache.get(id);
        if (record != null) {
            Object object = null;
            if (record.isUpdateChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                } else {
                    object = record.getObject();
                }
            } else if (record.isInsertChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                }
            } else if (record.isDeleteChange()) {
                if (!record.isCommitedState() && !record.isChangedByTransaction(transaction)) {
                    object = record.getObject();
                }
            } else {
                object = record.getObject();
            }

            if (object != null) {
                readObjects.putIfAbsent(id, new ObjectChange(cloner.cloneAndWrap(object, services.getStorage(), transaction), Change.read, System.nanoTime()));
                return readObjects.get(id).object;
            }
        }
        return null;
    }

    public void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            readByReferenceInternal(reference, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void readByReferenceInternal(final Object reference, final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
        if (readObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new HashMap<>());
            readObjects = readObjectsByTransaction.get(transactionId);
        }

        final Object objectId = field.get(reference);
        CacheRecord record = cache.get(objectId);
        if (record != null) {
            Object object = null;
            if (record.isUpdateChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                } else {
                    object = record.getObject();
                }
            } else if (record.isInsertChange()) {
                if (record.isCommitedState()) {
                    object = record.getObject();
                } else if (record.isChangedByTransaction(transaction)) {
                    object = record.getNewObject();
                }
            } else if (record.isDeleteChange()) {
                if (!record.isCommitedState() && !record.isChangedByTransaction(transaction)) {
                    object = record.getObject();
                }
            } else {
                object = record.getObject();
            }

            if (object != null) {
                // TODO check safety of this operation - reference must be one in context of transaction
                if (readObjects.containsKey(objectId)) {
                    final ObjectChange readObject = readObjects.get(objectId);
                    cloner.fillAndWrap(reference, readObject.object, services.getStorage(), transaction);
                } else {
                    cloner.fillAndWrap(reference, object, services.getStorage(), transaction);
                    readObjects.putIfAbsent(objectId, new ObjectChange(reference, Change.read, System.nanoTime()));   // reading by reference - only last read object is cached for this transaction
                }
                return;
            }
        }
        throw new XdStorageException("cannot load by reference object of " + reference.getClass() + " with idgeneration " + field.get(reference));
    }

    public void insert(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            insertInternal(newObject, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void insertInternal(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        log.debug("INSERT by transaction " + transaction.getTransactionId() + " OBJECT \r\n" + newObject);
        if (IXdStorageIdObservableWrapper.class.isAssignableFrom(newObject.getClass())) {
            final String transactionId = transaction.getTransactionId();

            Map<Object, IXdStorageIdObservableWrapper> records = changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
            if (records == null) {
                changesUnidentifiedObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                records = changesUnidentifiedObjectsByTransaction.get(transactionId);
            }
            records.put(newObject, (IXdStorageIdObservableWrapper) newObject);

            ((IXdStorageIdObservableWrapper) newObject).addObserver(new XdStorageAbstractIdObserver() {
                @Override
                public void onNewIdIsSet(final IXdStorageIdObservableWrapper wrapper, final Object id) {
                    CacheRecord record;

                    final Map<Object, IXdStorageIdObservableWrapper> wrappers = changesUnidentifiedObjectsByTransaction.get(transactionId);
                    if (wrappers != null) {
                        wrappers.remove(newObject);
                    }

                    final Object object = XdStorageObjectUtils.getWrappedObjectOrSameObject(newObject);

                    final CacheRecord tmpRecord;
                    try {
                        cache.putIfAbsent(id, tmpRecord = createInsertRecord(cloner.unwrapAndClone(object), transaction));
                        record = cache.get(id);
                        if (record != tmpRecord) {
                            throw new XdStorageRuntimeException("trying to double insert one object of " + newObject.getClass() + " with idgeneration " + id);
                        }

                        record.lock(transaction);
                    } catch (final XdStorageException e) {
                        throw new XdStorageRuntimeException("trying to lock object", e);
                    }

                    if (!record.canBeChangedByTransaction(transaction)) {
                        throw new XdStorageRuntimeException("object of " + newObject.getClass() + " with idgeneration " + id + " can not be changed by transaction " + transactionId);
                    }

                    Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
                    if(readObjects == null) {
                        readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                        readObjects = readObjectsByTransaction.get(transactionId);
                    }
                    readObjects.put(id, new ObjectChange(object, Change.insert, transaction.getTimestart()));

                    record.prepareCommit();

                    Map<Object, CacheRecord> map = changes.get(transactionId);
                    if (map == null) {
                        changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                        map = changes.get(transactionId);
                    }
                    map.put(id, record);
                }
            });
        } else {
            final String transactionId = transaction.getTransactionId();
            Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
            if(readObjects == null) {
                readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                readObjects = readObjectsByTransaction.get(transactionId);
            }

            final Object objectId = field.get(newObject);
            CacheRecord record = cache.get(objectId);
            if (objectId == null) {
                throw new XdStorageException("cannot store object of " + newObject.getClass() + " with null id");
            } else if (record == null) {
                final CacheRecord tmpRecord;
                cache.putIfAbsent(objectId, tmpRecord = createInsertRecord(cloner.unwrapAndClone(newObject), transaction));
                record = cache.get(objectId);
                if (record != tmpRecord) {
                    throw new XdStorageException("trying to double insert one object of " + newObject.getClass() + " with idgeneration " + objectId);
                }
                record.lock(transaction);
                if (!record.canBeChangedByTransaction(transaction)) {
                    record.unlock(transaction);
                    throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " can not be inserted by transaction " + transactionId);
                }
                readObjects.put(objectId, new ObjectChange(newObject, Change.insert, transaction.getTimestart()));
            } else {
                if (record.isChangedByTransaction(transaction)) {
                    if (record.isDeleteChange()) {
                        throw new XdStorageException("trying to insert deleted object of " + newObject.getClass() + " with idgeneration " + objectId);
                    } else if (record.isInsertChange()) {
                        throw new XdStorageException("trying to double insert one object of " + newObject.getClass() + " with idgeneration " + objectId);
                    } else {
                        throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " is exists");
                    }
                } else {
                    throw new XdStorageException("concurrent modification one object of " + newObject.getClass() + " with idgeneration " + objectId);
                }
            }
            Map<Object, CacheRecord> map = changes.get(transactionId);
            if (map == null) {
                changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                map = changes.get(transactionId);
            }
            map.put(objectId, record);
        }
    }

    public void update(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            updateInternal(newObject, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void updateInternal(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        log.debug("UPDATE by transaction " + transaction.getTransactionId() + " OBJECT \r\n" + newObject);

        final String transactionId = transaction.getTransactionId();

        final Object objectId = field.get(newObject);
        CacheRecord record = cache.get(objectId);

        if (record == null) {
            throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " does not exists");
        } else if (IXdStorageIdObservableWrapper.class.isAssignableFrom(newObject.getClass())) {
            record.lock(transaction);
            final CacheRecord lockedRecord = record;
            record = cache.get(objectId);
            if (record == null) {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("object of" + clazz + " with idgeneration " + objectId + " was deleted by another transaction");
            }

            final Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
            final ObjectChange change = readObjects != null ? readObjects.get(objectId) : null;

            if (change != null) {
                if (!record.canBeChangedByTransaction(transaction, change.readTimestamp)) {
                    lockedRecord.unlock(transaction);
                    throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
                }
            } else if (!record.canBeChangedByTransaction(transaction)) {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
            }
            record.markUpdate();

            ((IXdStorageIdObservableWrapper) newObject).addObserver(new XdStorageAbstractIdObserver() {
                @Override
                public void onNewIdIsSet(final IXdStorageIdObservableWrapper wrapper, final Object id) {
                    final Object object = XdStorageObjectUtils.getWrappedObjectOrSameObject(newObject);

                    final CacheRecord record = cache.get(id);
                    record.setNewObject(XdStorageObjectUtils.cloneObject(object));

                    Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
                    if(readObjects == null) {
                        readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                        readObjects = readObjectsByTransaction.get(transactionId);
                    }
                    readObjects.put(id, new ObjectChange(object, Change.update, change != null ? change.readTimestamp : transaction.getTimestart()));

                    Map<Object, CacheRecord> map = changes.get(transactionId);
                    if (map == null) {
                        changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                        map = changes.get(transactionId);
                    }
                    map.put(objectId, record);
                }
            });
        } else {
            Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
            if (readObjects == null) {
                readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                readObjects = readObjectsByTransaction.get(transactionId);
            }

            record.lock(transaction);
            final CacheRecord lockedRecord = record;
            record = cache.get(objectId);
            if (record == null) {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("object of" + clazz + " with idgeneration " + objectId + " was deleted by another transaction");
            }

            final ObjectChange change = readObjects.get(objectId);
            if (change != null) {
                if (!record.canBeChangedByTransaction(transaction, change.readTimestamp)) {
                    lockedRecord.unlock(transaction);
                    throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
                }
            } else if (!record.canBeChangedByTransaction(transaction)) {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
            }

            if (record.isReadChange()) {
                record.setNewObject(cloner.unwrapAndClone(newObject));
                record.markUpdate();
                readObjects.put(objectId, new ObjectChange(newObject, Change.update, change != null ? change.readTimestamp : transaction.getTimestart()));
            } else if (!record.isCommitedState() && record.isChangedByTransaction(transaction)) {
                if (record.isUpdateChange() || record.isInsertChange()) {
                    record.setNewObject(cloner.unwrapAndClone(newObject));
                    readObjects.put(objectId, new ObjectChange(newObject, Change.update, change != null ? change.readTimestamp : transaction.getTimestart()));
                } else {
                    lockedRecord.unlock(transaction);
                    throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " was deleted this transaction");
                }
            } else if (record.canBeChangedByTransaction(transaction)) {
                record.setNewObject(cloner.unwrapAndClone(newObject));
                record.markUpdate();
                readObjects.put(objectId, new ObjectChange(newObject, Change.update, change != null ? change.readTimestamp : transaction.getTimestart()));
            } else {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("concurrent modification one object of " + newObject.getClass() + " with idgeneration " + objectId);
            }

            Map<Object, CacheRecord> map = changes.get(transactionId);
            if (map == null) {
                changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                map = changes.get(transactionId);
            }
            map.put(objectId, record);
        }
    }

    public void delete(final Object objectId, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            deleteInternal(objectId, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void deleteInternal(final Object objectId, final XdStorageTransaction transaction) throws XdStorageException {
        log.debug("DELETE by transaction " + transaction.getTransactionId() + " OBJECT ID " + objectId);

        final String transactionId = transaction.getTransactionId();
        CacheRecord record = cache.get(objectId);
        if (record == null) {
            throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " does not exists");
        } else {
            Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
            if(readObjects == null) {
                readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                readObjects = readObjectsByTransaction.get(transactionId);
            }

            record.lock(transaction);
            final CacheRecord lockedRecord = record;
            record = cache.get(objectId);
            if (record == null) {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("object of" + clazz + " with idgeneration " + objectId + " was deleted by another transaction");
            }

            final ObjectChange change = readObjects.get(objectId);
            if (change != null) {
                if (!record.canBeChangedByTransaction(transaction, change.readTimestamp)) {
                    lockedRecord.unlock(transaction);
                    throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
                }
            } else if (!record.canBeChangedByTransaction(transaction)) {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " can not be deleted by transaction " + transactionId);
            }

            if (record.isReadChange()) {
                record.setNewObject(null);
                record.markDelete();
                readObjects.remove(objectId);
            } else if (!record.isCommitedState() && record.isChangedByTransaction(transaction)) {
                if (record.isInsertChange()) {
                    cache.remove(objectId);
                    readObjects.remove(objectId);
                } else if (record.isUpdateChange()) {
                    record.setNewObject(null);
                    record.markDelete();
                    readObjects.remove(objectId);
                } else {
                    lockedRecord.unlock(transaction);
                    throw new XdStorageException("trying to double delete one object of " + clazz + " with idgeneration " + objectId);
                }
            } else if (record.canBeChangedByTransaction(transaction)) {
                record.setNewObject(null);
                record.markDelete();
                readObjects.remove(objectId);
            } else {
                lockedRecord.unlock(transaction);
                throw new XdStorageException("concurrent modification one object of " + clazz + " with idgeneration " + objectId);
            }
        }

        Map<Object, CacheRecord> map = changes.get(transactionId);
        if (map == null) {
            changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            map = changes.get(transactionId);
        }
        map.put(objectId, record);
    }

    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            final Map<Object, CacheRecord> transactionChanges = changes.get(transaction.getTransactionId());
            final Map<Object, IXdStorageIdObservableWrapper> transactionUnidentifiedObjects = changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
            return (transactionChanges != null && !transactionChanges.isEmpty()) ||
                    (transactionUnidentifiedObjects != null && !transactionUnidentifiedObjects.isEmpty());
        } finally {
            transaction.finishCriticalSection();
        }
    }

    public void clear(final XdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();

        readObjectsByTransaction.remove(transactionId);
        changesUnidentifiedObjectsByTransaction.remove(transactionId);
        changes.remove(transactionId);
    }

    public void prepareCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        final String transactionId = transaction.getTransactionId();

        Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
        if(readObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            readObjects = readObjectsByTransaction.get(transactionId);
        }

        final Map<Object, CacheRecord> map = changes.get(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();
                Object oldObject = null, newObject = null;
                XdStorageObjectOperationType type = null;
                if (record.isInsertChange()) {
                    newObject = record.getNewObject();
                    type = XdStorageObjectOperationType.Insert;
                    newObject = readObjects.get(field.get(newObject)).object;
                } else if (record.isUpdateChange()) {
                    oldObject = record.getObject();
                    type = XdStorageObjectOperationType.Update;
                    newObject = readObjects.get(field.get(oldObject)).object;
                } else if (record.isDeleteChange()) {
                    oldObject = record.getObject();
                    type = XdStorageObjectOperationType.Delete;
                }

                if (log.isDebugEnabled()) {
                    log.debug("PREPARE COMMIT " + type + " by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + oldObject + "\r\n NEW OBJECT: " + newObject);
                }

                collector.addChangeObject(type, record.getId(),
                        oldObject != null ? XdStorageObjectUtils.cloneObject(oldObject) : null,
                        newObject != null ? newObject : null);

                record.prepareCommit();
            }
        }

        final Map<Object, IXdStorageIdObservableWrapper> wrappers = changesUnidentifiedObjectsByTransaction.get(transactionId);
        if (wrappers != null) {
            for (final IXdStorageIdObservableWrapper wrapper : wrappers.values()) {
                collector.addChangeObject(XdStorageObjectOperationType.Insert, null, null, wrapper);
            }
        }
    }

    public void performCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        final String transactionId = transaction.getTransactionId();

        Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
        if(readObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            readObjects = readObjectsByTransaction.get(transactionId);
        }

        final Map<Object, CacheRecord> map = changes.get(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();
                Object oldObject = null, newObject = null;
                XdStorageObjectOperationType type = null;
                if (record.isInsertChange()) {
                    newObject = record.getNewObject();
                    type = XdStorageObjectOperationType.Insert;
                    newObject = readObjects.get(field.get(newObject)).object;
                } else if (record.isUpdateChange()) {
                    oldObject = record.getObject();
                    type = XdStorageObjectOperationType.Update;
                    newObject = readObjects.get(field.get(oldObject)).object;
                } else if (record.isDeleteChange()) {
                    oldObject = record.getObject();
                    type = XdStorageObjectOperationType.Delete;
                }

                if (log.isDebugEnabled()) {
                    log.debug("PERFORM COMMIT " + type + " by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + oldObject + "\r\n NEW OBJECT: " + newObject);
                }

                collector.addChangeObject(type, record.getId(),
                        oldObject != null ? XdStorageObjectUtils.cloneObject(oldObject) : null,
                        newObject != null ? newObject : null);

                record.performCommit();
            }
        }

        final Map<Object, IXdStorageIdObservableWrapper> wrappers = changesUnidentifiedObjectsByTransaction.get(transactionId);
        if (wrappers != null) {
            for (final IXdStorageIdObservableWrapper wrapper : wrappers.values()) {
                collector.addChangeObject(XdStorageObjectOperationType.Insert, null, null, wrapper);
            }
        }
    }

    public void commit(final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        final Map<Object, CacheRecord> map = changes.remove(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();

                if (log.isDebugEnabled()) {
                    log.debug("COMMIT by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                }

                record.commit();
                if (record.getObject() == null)
                    cache.remove(record.getId());
                record.unlock(transaction);
            }
        }

        changesUnidentifiedObjectsByTransaction.remove(transactionId);
        readObjectsByTransaction.remove(transactionId);
    }

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        final Map<Object, CacheRecord> map = changes.remove(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();

                if (log.isDebugEnabled()) {
                    log.debug("ROLLBACK by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                }

                record.rollback();
                if (record.getObject() == null)
                    cache.remove(record.getId());
                record.unlock(transaction);
            }
        }

        changesUnidentifiedObjectsByTransaction.remove(transactionId);
        readObjectsByTransaction.remove(transactionId);
    }

    public void rollbackFailedCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> committed) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        final Map<Object, CacheRecord> map = changes.remove(transactionId);
        if (map != null) {
            for (final XdStorageObjectChange change : committed) {
                final Object object = change.oldObject;
                final Object id = field.get(object != null ? object : change.newObject);
                CacheRecord record = cache.get(id), oldRecord = map.get(id);
                if (record == null && object != null) { // was deleted by this transaction
                    cache.put(id, record = oldRecord != null ? oldRecord : createReadRecord(object));
                } else if (object == null) { // was inserted by this transaction
                    record = cache.remove(id);
                } else { // was updated by this transaction
                    cache.put(id, record = oldRecord != null ? oldRecord : createReadRecord(object));
                }
                if (record != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("ROLLBACK FAILED COMMIT by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                    }

                    record.rollback();
                    record.unlock(transaction);
                }
            }
        }

        changesUnidentifiedObjectsByTransaction.remove(transactionId);
        readObjectsByTransaction.remove(transactionId);
    }

    private CacheRecord createReadRecord(final Object object) {
        CacheRecord record = new CacheRecord();
        record.id = field.get(object);
        record.object = object;
        record.newObject = null;
        record.transaction = null;
        record.change = Change.read;
        record.state = State.undefined;
        return record;
    }

    private CacheRecord createInsertRecord(final Object newObject, final XdStorageTransaction transaction) {
        CacheRecord record = new CacheRecord();
        record.id = field.get(newObject);
        record.object = null;
        record.newObject = newObject;
        record.transaction = null;
        record.change = Change.insert;
        record.state = State.undefined;
        return record;
    }

    private enum State {
        undefined, locked, committed, rolledback
    }

    private enum Change {
        undefined, read, insert, update, delete
    }

    private class ObjectChange {

        public Object object;

        public final Change change;

        public final long readTimestamp;

        public ObjectChange(final Object object, final Change change, final long readTimestamp) {
            this.object = object;
            this.change = change;
            this.readTimestamp = readTimestamp;
        }
    }

    private class CacheRecord {

        private Object id;

        private Object object;

        private Object newObject;

        private XdStorageTransaction transaction;

        private long timestamp = Long.MIN_VALUE;

        private Change change;

        private Change previousChange;

        private State state;

        private State previousState;

        private boolean firstPhaseCommit;

        private boolean secondPhaseCommit;

        private AtomicBoolean locked = new AtomicBoolean(false);

        private CacheRecord() {
            // do nothing
        }

        public Object getId() {
            return this.id;
        }

        public boolean canBeChangedByTransaction(final XdStorageTransaction transaction) {
            boolean result = this.state == State.undefined
                    || (this.state == State.locked && this.transaction == transaction)
                    || (this.state == State.committed && transaction.getTimestart() > timestamp);

            if (log.isDebugEnabled()) {
                log.debug("canBeChangedByTransaction state: " + this.state
                        + " \r\n this.transaction: " + this.transaction + "(" + (this.transaction != null ? this.transaction.getTransactionId() : null)
                        + ")\r\n transaction: " + transaction + "(" + transaction.getTransactionId()
                        + ")\r\n this.timestamp: " + timestamp + "\r\n tran.timestart: " + transaction.getTimestart());
            }

            return result;
        }

        public boolean canBeChangedByTransaction(final XdStorageTransaction transaction, final long readObjectByTransactionTimestamp) {
            boolean result = this.state == State.undefined
                    || (this.state == State.locked && this.transaction == transaction)
                    || (this.state == State.committed && readObjectByTransactionTimestamp > timestamp);

            if (log.isDebugEnabled()) {
                log.debug("canBeChangedByTransaction state: " + this.state
                        + " \r\n this.transaction: " + this.transaction + "(" + (this.transaction != null ? this.transaction.getTransactionId() : null)
                        + ")\r\n transaction: " + transaction + "(" + transaction.getTransactionId()
                        + ")\r\n this.timestamp: " + timestamp + "\r\n tran.timestart: " + readObjectByTransactionTimestamp);
            }

            return result;
        }

        public boolean isChangedByTransaction(final XdStorageTransaction transaction) {
            return this.change != Change.read && this.transaction != null && this.transaction == transaction;
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

        public boolean isCommitedState() {
            return this.state == State.committed;
        }

        public boolean isReadChange() {
            return this.change == Change.read;
        }

        public boolean isInsertChange() {
            return this.change == Change.insert;
        }

        public boolean isUpdateChange() {
            return this.change == Change.update;
        }

        public boolean isDeleteChange() {
            return this.change == Change.delete;
        }

        public void markUpdate() {
            this.previousChange = this.change;
            this.change = Change.update;
            this.previousState = this.state;
            this.state = State.locked;
        }

        public void markDelete() {
            this.previousChange = this.change;
            this.change = Change.delete;
            this.previousState = this.state;
            this.state = State.locked;
        }

        private Map<String, Long> blockingTime = new HashMap<>();

        public void lock(final XdStorageTransaction transaction) throws XdStorageException {
            if (this.transaction == null || this.transaction != transaction) {
                synchronized (locked) {
                    while (locked.get()) {
                        try {
                            final String transactionId = transaction.getTransactionId();

                            final Long startTime = blockingTime.remove(transactionId);
                            final Long currentTime = System.currentTimeMillis();
                            if (startTime == null) {
                                blockingTime.put(transactionId, currentTime);
                            } else if ( (currentTime - startTime) >= transaction.getTimeout() ) {
                                throw new XdStorageException("transaction " + transactionId + " should be rolled back by timeout. Object with id " + id + " is locked for " + change);
                            } else {
                                blockingTime.put(transactionId, startTime);
                            }

                            locked.wait(transaction.getTimeout() / 2);
                        } catch (final InterruptedException e) {
                            throw new XdStorageException("locking object interrupted", e);
                        }
                    }
                    locked.set(true);
                }
                this.transaction = transaction;
            }
        }

        public void unlock(final XdStorageTransaction transaction) throws XdStorageException {
            if (this.transaction == transaction) {
                this.transaction = null;
                synchronized (locked) {
                    locked.set(false);
                    locked.notify();
                }
            } else {
                throw new XdStorageException("trying to release locking object by the transaction is not locked this object");
            }
        }

        public void prepareCommit() {
            firstPhaseCommit = true;
        }

        public void performCommit() {
            secondPhaseCommit = true;
        }

        public void commit() {
            if (!firstPhaseCommit || !secondPhaseCommit)
                throw new XdStorageRuntimeException("Illegal state of record to commit");

            this.object = this.newObject;
            this.newObject = null;
            this.state = State.committed;
            this.previousState = State.undefined;

            firstPhaseCommit = false;
            secondPhaseCommit = false;

            this.timestamp = System.nanoTime();
        }

        public void rollback() {
            this.newObject = null;
            this.state = this.previousState;
            this.previousState = State.undefined;
            this.change = previousChange;
            this.previousChange = Change.undefined;

            firstPhaseCommit = false;
            secondPhaseCommit = false;
        }
    }
}
