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
import org.flib.xdstorage.transaction.IXdStorageTransaction;
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

    /**
     * ЖУРНАЛ ОТКАТА (undo-change-log): записи кэша в ПОРЯДКЕ первой мутации транзакцией.
     * rollback() проигрывает журнал в ОБРАТНОМ порядке — это дает полный контроль над
     * откатом (каждая мутация имеет ровно одну точку отмены) и диагностическую картину
     * "что именно делала транзакция" при сбое отката. Очищается при commit (изменения
     * стали постоянными), rollback и clear.
     */
    private final Map<String, java.util.Deque<CacheRecord>> undoLogByTransaction = new ConcurrentHashMap<>();

    private final Map<String, Map<Object, IXdStorageIdObservableWrapper>> changesUnidentifiedObjectsByTransaction = new ConcurrentHashMap<>();

    private final Map<String, Map<Object, ObjectChange>> readObjectsByTransaction = new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    private AtomicBoolean isWrappableClass;

    private Class<?> clazz;

    private volatile XdStorageIsolationLevel isolationLevel = XdStorageIsolationLevel.SNAPSHOT;

    /**
     * ФАЗА 3B: кэш инфраструктуры B+Дерева (деревья/узлы). Граф — общее мутируемое
     * состояние: читатели получают ЖИВОЙ канонический инстанс (без клонирования и без
     * пос-транзакционных копий), record-level MVCC к структуре не применяется.
     */
    private final boolean infrastructureCache;

    public XdStorageIsolationLevel getIsolationLevel() {
        return isolationLevel;
    }

    public void setIsolationLevel(final XdStorageIsolationLevel isolationLevel) {
        this.isolationLevel = isolationLevel == null ? XdStorageIsolationLevel.SNAPSHOT : isolationLevel;
    }


    /** Журнал отката транзакции (создается при первой регистрации мутации). */
    private java.util.Deque<CacheRecord> undoLogOf(final String transactionId) {
        return undoLogByTransaction.computeIfAbsent(transactionId, k -> new java.util.ArrayDeque<>());
    }

    private Map<Object, ObjectChange> readObjectsOfTransaction(final XdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> readObjects = readObjectsByTransaction.get(transactionId);
        if (readObjects == null) {
            readObjectsByTransaction.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            readObjects = readObjectsByTransaction.get(transactionId);
        }
        return readObjects;
    }

    public XdStorageResourceCache(final XdStorageServicesLocator services, final Class<?> clazz, final XdStorageObjectIdField field) {
        this.services = services;
        this.field = field;
        this.infrastructureCache = clazz != null && clazz.getName().contains("org.flib.xdstorage.btree");
        this.clazz = clazz;
        this.cloner = this.services.getCloner();
//        setIsolationLevel(IsolationLevel.READ_COMMITTED);
    }

    public long getObjectsCount() {
        return cache.size();
    }

    public void fillCache(final Collection<Object> objects) {
        for (final Object object : objects) {
            // ФАЗА 3B: parse-копия уступает место живому каноническому инстансу (если он есть)
            final Object objectCanonical = adoptCanonicalInstance(object);
            cache.putIfAbsent(field.get(objectCanonical), createReadRecord(objectCanonical));
        }
    }

    public <T> void fillCacheOnlyIfNotExists(final Collection<T> objects) {
        for (final Object object : objects) {
            final Object objectCanonical = adoptCanonicalInstance(object);
            cache.putIfAbsent(field.get(objectCanonical), createReadRecord(objectCanonical));
        }
    }

    /**
     * ФАЗА 3B: для инфраструктуры B+Дерева — канонизация parse-копии при наполнении кэша.
     * Если живой инстанс уже зарегистрирован (писатель мутирует его), parse-копия отбрасывается
     * и записи кэша получают канонический инстанс; иначе parse-копия сама регистрируется.
     */
    private Object adoptCanonicalInstance(final Object parsed) {
        if (!infrastructureCache || parsed == null) {
            return parsed;
        }
        final XdStorageCanonicalInstanceRegistry registry = services.getCanonicalInstances();
        if (registry == null) {
            return parsed; // мок-locator без реестра (юнит-сьюты)
        }
        final Object parsedId = field.get(parsed);
        if (parsedId == null) {
            return parsed;
        }
        final Object canonical = registry.adoptOrRegister(clazz, parsedId, parsed);
        if (log.isDebugEnabled() && canonical != parsed) {
            log.debug("infra instance {}#{} adopted from the canonical registry; parsed copy discarded", clazz.getSimpleName(), parsedId);
        }
        return canonical;
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
        Map<Object, ObjectChange> tmpReadObjects = readObjectsOfTransaction(transaction);

        for (final T object : objects) {
            final Object objectCanonical = adoptCanonicalInstance(object);
            final Object objectId = field.get(objectCanonical);
            final Object objectToCache = objectCanonical;
            final Object clonedObject = infrastructureCache
                    ? objectToCache
                    : cloner.cloneAndWrap(objectToCache, services.getStorage(), transaction);
            tmpReadObjects.putIfAbsent(objectId, new ObjectChange(clonedObject, Change.read, System.nanoTime()));
            result.add((T) tmpReadObjects.get(objectId).object);
            cache.putIfAbsent(objectId, createReadRecord(objectToCache));
        }
    }

    private boolean isWrappableObject(final Object object) {
        if (isWrappableClass == null) {
            lock.lock();
            try {
                if (isWrappableClass == null) {
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
        final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);

        final String transactionId = transaction.getTransactionId();
        Map<Object, ObjectChange> tmpReadObjects = readObjectsOfTransaction(transaction);
        final Map<Object, ObjectChange> readObjects = tmpReadObjects;

        final List<T> result = Collections.synchronizedList(new java.util.LinkedList<>());

        // БЛOК 1: СУБД SNAPSHOT VIEW БАРЬЕР (Чтение по предикату)
        Object[] recordsSnapshot = cache.values().toArray();

        for (final Object node : recordsSnapshot) {
            if (hasError.get()) {
                break;
            }

            if (node == null) {
                continue;
            }
            CacheRecord record = (CacheRecord) node;

            Object object = null;
            synchronized (record) {
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
                    if (!record.isCommitedState()) {
                        if (!record.isChangedByTransaction(transaction)) {
                            object = record.getObject();
                        }
                    } else if (servesSnapshotVersion(transaction, record)) {
                        object = record.prevObject;
                    }
                } else {
                    object = record.getObjectForTransaction(transaction);
                }
            }

            if (object != null) {
                try {
                    final Object objectId = field.get(object);
                    // Phase D-1: в READ COMMITTED перечитываем свежую копию ТОЛЬКО когда версия
                    // записи изменилась с момента материализации (чужой mark/commit) или это своя
                    // мутация. Раньше каждый скан переклонировал ВСЕ строки — O(N) клонов на скан.
                    final boolean rcRefresh = isolationLevel == XdStorageIsolationLevel.READ_COMMITTED;
                    final ObjectChange materialized = readObjects.get(objectId);
                    final boolean needsRematerialize = materialized == null
                            || record.isChangedByTransaction(transaction)
                            || (rcRefresh && materialized.recordVersion != record.getVersion());
                    if (needsRematerialize) {
                        Object cloned = cloner.cloneAndWrap(object, services.getStorage(), transaction);
                        final ObjectChange oc = new ObjectChange(cloned, Change.read, System.nanoTime());
                        oc.recordVersion = record.getVersion();
                        if (rcRefresh) {
                            readObjects.put(objectId, oc);
                        } else {
                            readObjects.putIfAbsent(objectId, oc);
                        }
                    }
                    Object objectToReturn = readObjects.get(objectId).object;

                    // =========================================================================
                    // СУБД-БАРЬEР БEЗOПAСНOСТИ ПРEДИКАТA: Если объект не удалось обернуть в
                    // unmodifiable-прокси (wrapAsUnmodifiableObject вернул null), мы ОБЯЗАНЫ
                    // проверить предикат по сырому материализованному объекту, а не выбрасывать
                    // объект из выборки. Иначе диапазонное чтение молча теряет записи, которые
                    // видит полный скан read(transaction) — нарушение контракта Snapshot Isolation!
                    // =========================================================================
                    if (objectToReturn != null) {
                        final Object wrappedObject = org.flib.xdstorage.utils.XdStorageObjectUtils.wrapAsUnmodifiableObject(objectToReturn, services.getStorage(), transaction);
                        final Object objectForPredicate = wrappedObject != null ? wrappedObject : objectToReturn;
                        if (predicate.passed((T) objectForPredicate)) {
                            result.add((T) objectToReturn);
                        }
                    }
                } catch (final Throwable e) {
                    if (!hasError.get()) {
                        hasError.set(true);
                        exceptions[0] = new XdStorageException(e);
                    }
                }
            }
        }

        if (exceptions[0] != null)
            throw new XdStorageRuntimeException(exceptions[0]);

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
        if (unidentifiedObjects != null) {
            Object[] unidentifiedSnapshot = unidentifiedObjects.values().toArray();
            for (Object nodeWrapper : unidentifiedSnapshot) {
                if (hasError.get()) break;
                if (nodeWrapper == null) continue;
                IXdStorageIdObservableWrapper wrapper = (IXdStorageIdObservableWrapper) nodeWrapper;
                try {
                    if (wrapper != null && predicate.passed((T) wrapper)) {
                        result.add((T) wrapper);
                    }
                } catch (final Throwable e) {
                    if (!hasError.get()) {
                        hasError.set(true);
                        exceptions[0] = new XdStorageException(e);
                    }
                }
            }

            if (exceptions[0] != null)
                throw new XdStorageRuntimeException(exceptions[0]);
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
        Map<Object, ObjectChange> tmpReadObjects = readObjectsOfTransaction(transaction);
        final Map<Object, ObjectChange> readObjects = tmpReadObjects;

        // =========================================================================
        // СНАЙПEРСКИЙ ФИКС CLASSCASTEXCEPTION: Снимаем чистый Snapshot-массив Object[]!
        // Избавляемся от хрупкого принудительного каста (CacheRecord[]) всего массива.
        // =========================================================================
        Object[] recordsSnapshot = cache.values().toArray();

        Arrays.stream(recordsSnapshot).forEach(node -> {
            if (hasError.get() || node == null) {
                return;
            }
            CacheRecord record = (CacheRecord) node;

            Object object = null;
            synchronized (record) {
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
                    if (!record.isCommitedState()) {
                        if (!record.isChangedByTransaction(transaction)) {
                            object = record.getObject();
                        }
                    } else if (servesSnapshotVersion(transaction, record)) {
                        object = record.prevObject;
                    }
                } else {
                    object = record.getObjectForTransaction(transaction);
                }
            }

            try {
                if (object != null) {
                    final Object objectId = field.get(object);
                    // READ COMMITTED: если чужой коммит изменил запись после материализации —
                    // сбрасываем устаревшую копию, ниже она пересоздастся (как в readInternal(id))
                    final ObjectChange materialized = readObjects.get(objectId);
                    if (materialized != null && isolationLevel == XdStorageIsolationLevel.READ_COMMITTED
                            && materialized.recordVersion != record.getVersion()) {
                        readObjects.remove(objectId);
                    }
                    final Object objectToWrap;
                    if (readObjects.containsKey(objectId)) {
                        objectToWrap = readObjects.get(objectId).object;
                    } else if (isWrappableObject(object)) {
                        objectToWrap = object;
                    } else {
                        objectToWrap = XdStorageObjectUtils.cloneObject(object);
                        if (isolationLevel == XdStorageIsolationLevel.READ_COMMITTED) {
                            // запоминаем версию записи, чтобы следующее чтение увидело устаревание копии
                            final ObjectChange refreshed = new ObjectChange(objectToWrap, Change.read, System.nanoTime());
                            refreshed.recordVersion = record.getVersion();
                            readObjects.put(objectId, refreshed);
                        }
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
            throw new XdStorageRuntimeException(exceptions[0]);

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects = changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
        if (unidentifiedObjects != null) {
            unidentifiedObjects.values().stream().forEach(wrapper -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    if (wrapper != null) {
                        watcher.watch((T) wrapper);
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
                throw new XdStorageRuntimeException(exceptions[0]);
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
        Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

        final List<T> result = Collections.synchronizedList(new ArrayList<>());

        // БЛOК 1: СУБД SNAPSHOT VIEW БАРЬЕР (Полный скан кэша)
        Object[] recordsSnapshot = cache.values().toArray();

        for (final Object node : recordsSnapshot) {
            if (node == null) continue;
            CacheRecord record = (CacheRecord) node;

            Object object = null;
            synchronized (record) {
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
                    if (!record.isCommitedState()) {
                        if (!record.isChangedByTransaction(transaction)) {
                            object = record.getObject();
                        }
                    } else if (servesSnapshotVersion(transaction, record)) {
                        object = record.prevObject;
                    }
                } else {
                    object = record.getObjectForTransaction(transaction);
                }
            }

            if (object != null) {
                final Object objectId = field.get(object);
                synchronized (result) {
                    synchronized (record) {
                        // Phase D-1: в RC перечитываем ТОЛЬКО при изменившейся версии записи
                        // (или своя мутация); SNAPSHOT — материализация при отсутствии, как раньше
                        final ObjectChange materialized = readObjects.get(objectId);
                        final boolean needsRematerialize = materialized == null
                                || record.isChangedByTransaction(transaction)
                                || (isolationLevel == XdStorageIsolationLevel.READ_COMMITTED
                                    && materialized.recordVersion != record.getVersion());
                        if (needsRematerialize) {
                            Object cloned = cloner.cloneAndWrap(object, services.getStorage(), transaction);
                            final ObjectChange oc = new ObjectChange(cloned, Change.read, System.nanoTime());
                            oc.recordVersion = record.getVersion();
                            readObjects.put(objectId, oc);
                        }
                    }
                    result.add((T) readObjects.get(objectId).object);
                }
            }
        }

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects = changesUnidentifiedObjectsByTransaction.get(transaction.getTransactionId());
        if (unidentifiedObjects != null) {
            Object[] unidentifiedSnapshot = unidentifiedObjects.values().toArray();
            for (final Object wrapper : unidentifiedSnapshot) {
                if (wrapper != null) {
                    result.add((T) wrapper);
                }
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
        Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

        final List<T> result = Collections.synchronizedList(new ArrayList<>());

        // БЛOК 1: СУБД SNAPSHOT VIEW БАРЬЕР (Unsafe скан)
        Object[] recordsSnapshot = cache.values().toArray();

        for (final Object node : recordsSnapshot) {
            if (node == null) continue;
            CacheRecord record = (CacheRecord) node;

            Object object = null;
            synchronized (record) {
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
                    if (!record.isCommitedState()) {
                        if (!record.isChangedByTransaction(transaction)) {
                            object = record.getObject();
                        }
                    } else if (servesSnapshotVersion(transaction, record)) {
                        object = record.prevObject;
                    }
                } else {
                    // WRITE-OUT (коммит/откат ресурса): для чужих закоммиченных записей берем
                    // ПОСЛЕДНЕЕ закоммиченное состояние, а не снапшотную route'ировку — иначе
                    // коммиттер, начавший раньше соседа, перезаписал бы файл его старой версией
                    object = record.getObject();
                }
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
            Object[] unidentifiedSnapshot = unidentifiedObjects.values().toArray();
            for (final Object wrapper : unidentifiedSnapshot) {
                if (wrapper != null) {
                    result.add((T) wrapper);
                }
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
        Map<Object, ObjectChange> tmpReadObjects = readObjectsOfTransaction(transaction);
        final Map<Object, ObjectChange> readObjects = tmpReadObjects;

        CacheRecord record = cache.get(id);

        // ФАЗА 3B: барьер невидимой незакоммиченной вставки/апдейта для инфраструктуры дерева
        // (реестр канонических инстансов теперь канонизирует parse-копии при fillCache — R-19/R-20)
        if (infrastructureCache && record != null && waitForRecordResolution(record, id, transaction)) {
            record = cache.get(id);
        }
        // ФАЗА 3B: записи нет (eviction / откат создателя удалил незакоммиченную запись) —
        // канонический ЖИВОЙ инстанс остается в реестре; восстанавливаем запись кэша из него.
        // Без этого читатель/писатель дерева получал null ("does not exists") при живом графе.
        if (infrastructureCache && record == null) {
            final XdStorageCanonicalInstanceRegistry registry = services.getCanonicalInstances();
            final Object canonical = registry != null ? registry.get(clazz, id) : null;
            if (canonical != null) {
                cache.putIfAbsent(id, createReadRecord(canonical));
                record = cache.get(id);
            }
        }
        ObjectChange materialized = readObjects.get(id);

        // READ COMMITTED: если версия записи изменилась с момента материализации
        // (чужой коммит) — сбрасываем устаревшую материализацию, ниже она пересоздастся.
        // SNAPSHOT: отдаем материализованную копию как есть (повторяемое чтение).
        if (materialized != null && isolationLevel == XdStorageIsolationLevel.READ_COMMITTED
                && (record == null || materialized.recordVersion != record.getVersion())) {
            readObjects.remove(id);
            materialized = null;
        }

        // Если объект уже материализован текущей транзакцией — мгновенно возвращаем его (Identity)
        if (materialized != null) {
            return materialized.object;
        }

        if (record != null) {
            Object object = null;

            if (infrastructureCache) {
                // ФАЗА 3B: живой канонический граф — без клонирования, без пос-транзакционных копий
                synchronized (record) {
                    object = record.getObject() != null
                            ? record.getObject()
                            : (record.isChangedByTransaction(transaction) ? record.getNewObject() : null);
                }
                if (object != null) {
                    readObjects.putIfAbsent(id, new ObjectChange(object, Change.read, System.nanoTime()));
                    return readObjects.get(id).object;
                }
                if (Boolean.getBoolean("xdstorage.chain.debug")) {
                    synchronized (record) {
                        System.out.println("[CHAIN] INFRA-NULL id=" + id + " tx=" + transaction.getTransactionId()
                                + " state=" + record.state + " change=" + record.change
                                + " objectNull=" + (record.object == null)
                                + " own=" + record.isChangedByTransaction(transaction)
                                + " owner=" + (record.transaction != null ? record.transaction.getTransactionId() : null));
                    }
                }
                return null;
            }

            // Канонический MVCC-барьер вычисления видимости в оперативной памяти
            synchronized (record) {
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
                    if (!record.isCommitedState()) {
                        if (!record.isChangedByTransaction(transaction)) {
                            object = record.getObject();
                        }
                    } else if (servesSnapshotVersion(transaction, record)) {
                        object = record.prevObject;
                    }
                } else {
                    object = record.getObjectForTransaction(transaction);
                }

                if (object != null) {
                    if (!readObjects.containsKey(id) || record.isChangedByTransaction(transaction)) {
                        Object cloned = cloner.cloneAndWrap(object, services.getStorage(), transaction);
                        final ObjectChange oc = new ObjectChange(cloned, Change.read, System.nanoTime());
                        oc.recordVersion = record.getVersion();
                        readObjects.put(id, oc);
                    }
                    return readObjects.get(id).object;
                }
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
        Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

        final Object objectId = field.get(reference);
        if (objectId == null) {
            return;
        }

        CacheRecord record = cache.get(objectId);

        // ФАЗА 3B: записи нет — восстанавливаем из реестра канонических инстансов (см. readInternal)
        if (infrastructureCache && record == null) {
            final XdStorageCanonicalInstanceRegistry registry = services.getCanonicalInstances();
            final Object canonical = registry != null ? registry.get(clazz, objectId) : null;
            if (canonical != null) {
                cache.putIfAbsent(objectId, createReadRecord(canonical));
                record = cache.get(objectId);
            }
        }

        // =========================================================================
        // ФАЗА 3B: БАРЬЕР НЕВИДИМОЙ НЕЗАКОММИЧЕННОЙ ВСТАВКИ (только инфраструктура дерева).
        // Параллельный писатель публикует указатели на новые узлы (сплиты) немедленно,
        // а сами узлы видимы после коммита. Читатель, зашедший по такому указателю,
        // ограниченно ждет развязки чужой транзакции (коммит -> узел виден; откат ->
        // запись изъята из кэша и честно считается отсутствующей).
        // =========================================================================
        if (infrastructureCache && record != null && waitForRecordResolution(record, objectId, transaction)) {
            record = cache.get(objectId);
        }

        Object object = null;

        if (record != null) {
            if (infrastructureCache) {
                // ФАЗА 3B: живой канонический инстанс — наполняем ссылку из канона
                synchronized (record) {
                    object = record.getObject() != null
                            ? record.getObject()
                            : (record.isChangedByTransaction(transaction) ? record.getNewObject() : null);
                }
                if (object != null) {
                    cloner.fillAndWrap(reference, object, services.getStorage(), transaction);
                    readObjects.putIfAbsent(objectId, new ObjectChange(reference, Change.read, System.nanoTime()));
                    return;
                }
                throw new XdStorageException("cannot load by reference object of " + reference.getClass() + " with idgeneration " + field.get(reference));
            }
            synchronized (record) {
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
                    if (!record.isCommitedState()) {
                        if (!record.isChangedByTransaction(transaction)) {
                            object = record.getObject();
                        }
                    } else if (servesSnapshotVersion(transaction, record)) {
                        object = record.prevObject;
                    }
                } else {
                    object = record.getObjectForTransaction(transaction);
                }

                if (object != null) {
                    // READ COMMITTED: наполняем ссылку последним закоммиченным состоянием,
                    // а не устаревшей материализацией из транзитной карты
                    cloner.fillAndWrap(reference, object, services.getStorage(), transaction);
                    readObjects.putIfAbsent(objectId, new ObjectChange(reference, Change.read, System.nanoTime()));
                    return;
                }
            }
        }

        throw new XdStorageException("cannot load by reference object of " + reference.getClass() + " with idgeneration " + field.get(reference));
    }

    /**
     * ФАЗА 2: ограниченное ожидание развязки чужой незакоммиченной мутации записи.
     * Ждем только пока запись остается невидимой незакоммиченной вставкой/апдейтом
     * ЖИВОЙ чужой транзакции. Коммит снимает ожидание (узел становится видим),
     * откат изымает запись из кэша (ожидание прекращается — записи больше нет),
     * смерть владельца обрабатывается обычным контуром очистки.
     */
    /**
     * ФАЗА 3B: ограниченное ожидание развязки чужой незакоммиченной мутации записи
     * (ТОЛЬКО инфраструктурные кэши — вызовы скоупятся флагом infrastructureCache).
     * Ждем пока запись остается невидимой незакоммиченной вставкой/апдейтом ЖИВОЙ
     * чужой транзакции. Возвращает true, если ждали и запись могла смениться
     * (вызывающий обязан перечитать cache.get). Бюджет — таймаут транзакции (до 3 с):
     * создатель ленивого дерева держит его незакоммиченным весь СВОЙ tx — под
     * конкуренцией lockForCommit это секунды, и короткий бюджет давал конкурентное
     * ВТОРОЕ дерево (createTreeLock double-check) и "does not exists" у апдейтеров.
     */
    private boolean waitForRecordResolution(final CacheRecord initialRecord, final Object objectId,
                                            final XdStorageTransaction transaction) throws XdStorageException {
        if (!isInvisibleUncommittedMutation(initialRecord, transaction)) {
            return false;
        }
        final long timeout = Math.min(transaction.getTimeout() > 0 ? transaction.getTimeout() : 5000L, 3000L);
        final long deadlineNano = System.nanoTime() + timeout * 1_000_000L;
        CacheRecord record = initialRecord;

        while (isInvisibleUncommittedMutation(record, transaction)) {
            if (System.nanoTime() >= deadlineNano) {
                // Чужая транзакция держит инфраструктурную запись невидимой дольше бюджета:
                // читатель идет дальше с "честным отказом" — этот факт обязан быть в логе.
                log.warn("infra record {} stayed invisible past the wait budget ({} ms, owner tx: {}) — serving absent",
                        objectId, timeout, record.transaction != null ? record.transaction.getTransactionId() : null);
                return true; // честный отказ дальше по контракту "cannot load by reference"
            }
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new XdStorageException("waiting for record resolution interrupted", e);
            }
            record = cache.get(objectId);
            if (record == null) {
                return false; // запись изъята (откат вставки) — записи больше нет
            }
        }
        return true;
    }

    private boolean isInvisibleUncommittedMutation(final CacheRecord record, final XdStorageTransaction transaction) {
        if (record == null || record.isCommitedState()) {
            return false;
        }
        if (record.isChangedByTransaction(transaction)) {
            return false; // своя мутация — видим через newObject
        }
        if (!record.isInsertChange() && !record.isUpdateChange()) {
            return false; // delete/read-состояния обрабатываются обычным MVCC-переключателем
        }
        final XdStorageTransaction owner = record.transaction;
        return owner != null && services.getTransactionsManager().isTransactionAlive(owner);
    }

    public void insert(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            final String transactionId = transaction.getTransactionId();

            // ГЛОБАЛЬНОЕ ИСПРАВЛЕНИЕ СУБД: Защита от избыточных повторных вставок.
            // Если объект уже имеет ID и зарегистрирован в карте изменений текущей транзакции,
            // мы fail-safe завершаем метод, предотвращая коллизии MVCC-версий на индексах классов!
            if (!IXdStorageIdObservableWrapper.class.isAssignableFrom(newObject.getClass())) {
                final Object objectId = field.get(newObject);
                final Map<Object, CacheRecord> map = changes.get(transactionId);
                if (objectId != null && map != null && map.containsKey(objectId)) {
                    final CacheRecord existingRecord = map.get(objectId);
                    if (existingRecord.isInsertChange() || existingRecord.isUpdateChange()) {
                        return; // Объект уже находится в транзакционном контуре, игнорируем дубликат
                    }
                }
            }

            insertInternal(newObject, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void insertInternal(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        log.debug("INSERT by transaction {} of {}", transaction.getTransactionId(), newObject.getClass().getSimpleName());
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
                        // ИДЕНТИЧНОСТЬ B+ ДЕРЕВА: ноды индекса сохраняются живой инстанцией
                        cache.putIfAbsent(id, tmpRecord = createInsertRecord(materializeForCache(object), transaction));
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

                    Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);
                    readObjects.put(id, new ObjectChange(object, Change.insert, transaction.getTimestart()));

                    synchronized (record) {
                        record.prepareCommit();
                    }

                    Map<Object, CacheRecord> map = changes.get(transactionId);
                    if (map == null) {
                        changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                        map = changes.get(transactionId);
                    }
                    if (map.put(id, record) == null) {
                        undoLogOf(transactionId).addLast(record); // первая мутация записи — в журнал отката
                    }
                }
            });
        } else {
            final String transactionId = transaction.getTransactionId();
            Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

            final Object objectId = field.get(newObject);
            if (objectId == null) {
                // Барьер null-ID обязан стоять ДО обращения к CHM: cache.get(null)
                // падает с голым NullPointerException, съедая осмысленную диагностику
                throw new XdStorageException("cannot store object of " + newObject.getClass() + " with null id");
            }
            CacheRecord record = cache.get(objectId);
            if (record == null) {
                final CacheRecord tmpRecord;
                // ИДЕНТИЧНОСТЬ B+ ДЕРЕВА: ноды индекса сохраняются живой инстанцией,
                // чтобы граф children/next не расходился между кэшем и памятью после коммита
                cache.putIfAbsent(objectId, tmpRecord = createInsertRecord(materializeForCache(newObject), transaction));
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
                synchronized (record) {
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
            }
            Map<Object, CacheRecord> map = changes.get(transactionId);
            if (map == null) {
                changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
                map = changes.get(transactionId);
            }
            if (map.put(objectId, record) == null) {
                undoLogOf(transactionId).addLast(record); // первая мутация записи — в журнал отката
            }
        }
    }

    // =========================================================================
    // БЛОК 3: ПУЛЕНЕПРОБИВАЕМЫЙ КОНТУР ОБНОВЛЕНИЯ (Вынос локов из-под критической секции)
    // =========================================================================

    /**
     * Классификация инфраструктурных объектов B+ Дерева индексов.
     * Такие объекты обязаны храниться в кэше РЕФЕРЕНСОМ НА ЖИВУЮ ИНСТАНЦИЮ, а не клоном:
     * их поля (children/nextTreeNodeOnThisLevel/parent) образуют единый граф в памяти JVM,
     * и любая подмена инстанса клоном разрывает связность структуры между транзакциями.
     */
    private static boolean isBTreeInfrastructureObject(final Object object) {
        final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(object.getClass());
        return entityClass == org.flib.xdstorage.btree.XdStorageBTree.class
                || entityClass == org.flib.xdstorage.btree.XdStorageBTreeNode.class
                || entityClass.getName().contains("org.flib.xdstorage.btree");
    }

    /**
     * Материализация объекта для записи в кэш: живая инстанция для B+ Дерева, изолированный клон для доменных объектов.
     */
    private Object materializeForCache(final Object newObject) throws XdStorageException {
        final Object materialized = isBTreeInfrastructureObject(newObject)
                ? XdStorageObjectUtils.getWrappedObjectOrSameObject(newObject)
                : cloner.unwrapAndClone(newObject);
        // ФАЗА 3B: живой инстанс инфраструктуры становится каноническим при первой записи
        final XdStorageCanonicalInstanceRegistry registry = services.getCanonicalInstances();
        if (materialized != null && isBTreeInfrastructureObject(materialized) && registry != null) {
            final Object materializedId = field.get(materialized);
            if (materializedId != null) {
                final Object canonical = registry.adoptOrRegister(clazz, materializedId, materialized);
                if (Boolean.getBoolean("xdstorage.chain.debug")) {
                    System.out.println("[CHAIN] REGISTER class=" + clazz.getName() + " id=" + materializedId
                            + " instance@" + System.identityHashCode(materialized)
                            + (canonical != materialized ? " ADOPTED-EXISTING@" + System.identityHashCode(canonical) : " NEW"));
                }
                return canonical != null ? canonical : materialized;
            } else if (Boolean.getBoolean("xdstorage.chain.debug")) {
                System.out.println("[CHAIN] REGISTER-SKIP-NULL-ID class=" + clazz.getName()
                        + " object@" + System.identityHashCode(materialized));
            }
        }
        return materialized;
    }

    public void update(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        final Object objectId = field.get(newObject);
        CacheRecord record = cache.get(objectId);

        if (record == null) {
            throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " does not exists");
        }

        // Запрашиваем лок ДО входа в глобальную критическую секцию транзакции!
        record.lock(transaction);

        transaction.startCriticalSection();
        try {
            final String transactionId = transaction.getTransactionId();

            final Map<Object, CacheRecord> map = changes.get(transactionId);
            if (objectId != null && map != null && map.containsKey(objectId)) {
                final CacheRecord existingRecord = map.get(objectId);
                if (existingRecord.isInsertChange() || existingRecord.isUpdateChange()) {
                    // ИДЕНТИЧНОСТЬ B+ ДЕРЕВА: быстрый путь повторного апдейта обязан подчиняться
                    // тому же правилу живой инстанции, что и полный путь — иначе коммит пишет
                    // устаревший клон ноды на диск и рвет цепочку листьев индекса.
                    existingRecord.setNewObject(materializeForCache(newObject));

                    // =========================================================================
                    // СИНХРОНИЗАЦИЯ ТРАНЗИТНОГО ОБЪЕКТА С БАРЬEРОМ ВЫГРУЗКИ: readUnsafe() при
                    // выписке на диск предпочитает readObjects[id].object. Без обновления этой
                    // карты быстрый путь оставлял там ИСХОДНУЮ инстанцию с момента insert(),
                    // и все мутации, выполненные через другую инстанцию (например, подгруженный
                    // стаб ссылки B+ Дерева), молча терялись при коммите!
                    // =========================================================================
                    Map<Object, ObjectChange> transitObjects = readObjectsByTransaction.get(transactionId);
                    if (transitObjects != null) {
                        final ObjectChange previous = transitObjects.get(objectId);
                        if (previous != null) {
                            transitObjects.put(objectId, new ObjectChange(newObject, Change.update, previous.readTimestamp));
                        }
                    }
                    return;
                }
            }

            updateInternalAfterLock(newObject, transaction, record);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void updateInternalAfterLock(final Object newObject, final XdStorageTransaction transaction, CacheRecord record) throws XdStorageException {
        log.debug("UPDATE by transaction {} of {}", transaction.getTransactionId(), newObject.getClass().getSimpleName());

        final String transactionId = transaction.getTransactionId();
        final Object objectId = field.get(newObject);

        // Перечитываем актуальное состояние ноды после прохождения барьера ожидания лока
        record = cache.get(objectId);
        if (record == null) {
            throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " was deleted by another transaction");
        }

        Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

        final ObjectChange change = readObjects.get(objectId);
        synchronized (record) {
            if (change != null) {
                if (!record.canBeChangedByTransaction(transaction, change.readTimestamp)) {
                    throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
                }
            } else if (!record.canBeChangedByTransaction(transaction)) {
                throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
            }

            if (record.isReadChange()) {
                record.setNewObject(materializeForCache(newObject));
                record.markUpdate(transaction);
                readObjects.put(objectId, new ObjectChange(newObject, Change.update, change != null ? change.readTimestamp : transaction.getTimestart()));
            } else if (!record.isCommitedState() && record.isChangedByTransaction(transaction)) {
                if (record.isUpdateChange() || record.isInsertChange()) {
                    record.setNewObject(materializeForCache(newObject));
                    readObjects.put(objectId, new ObjectChange(newObject, Change.update, change != null ? change.readTimestamp : transaction.getTimestart()));
                } else {
                    throw new XdStorageException("object of " + newObject.getClass() + " with idgeneration " + objectId + " was deleted this transaction");
                }
            } else if (record.canBeChangedByTransaction(transaction)) {
                record.setNewObject(materializeForCache(newObject));
                record.markUpdate(transaction);
                readObjects.put(objectId, new ObjectChange(newObject, Change.update, change != null ? change.readTimestamp : transaction.getTimestart()));
            } else {
                throw new XdStorageException("concurrent modification one object of " + newObject.getClass() + " with idgeneration " + objectId);
            }
        }

        Map<Object, CacheRecord> map = changes.get(transactionId);
        if (map == null) {
            changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            map = changes.get(transactionId);
        }
        if (map.put(objectId, record) == null) {
            undoLogOf(transactionId).addLast(record); // первая мутация записи — в журнал отката
        }
    }

    // =========================================================================
    // БЛОК 2: ПУЛЕНЕПРОБИВАЕМЫЙ КОНТУР УДАЛЕНИЯ (Вынос локов из-под критической секции)
    // =========================================================================
    public void delete(final Object objectId, final XdStorageTransaction transaction) throws XdStorageException {
        CacheRecord record = cache.get(objectId);
        if (record == null) {
            throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " does not exists");
        }

        // Запрашиваем лок ДО входа в глобальную критическую секцию транзакции!
        // Поток дождется освобождения записи, не подвешивая монитор сессии для коммитов.
        record.lock(transaction);

        transaction.startCriticalSection();
        try {
            deleteInternalAfterLock(objectId, transaction, record);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void deleteInternalAfterLock(final Object objectId, final XdStorageTransaction transaction, CacheRecord record) throws XdStorageException {
        log.debug("DELETE by transaction {} of id {}", transaction.getTransactionId(), objectId);

        final String transactionId = transaction.getTransactionId();

        // Перечитываем ноду из CHM, так как за время ожидания лока её могли подменить
        record = cache.get(objectId);
        if (record == null) {
            throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " was deleted by another transaction");
        }

        Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

        synchronized (record) {
            final ObjectChange change = readObjects.get(objectId);
            if (change != null) {
                if (!record.canBeChangedByTransaction(transaction, change.readTimestamp)) {
                    throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " can not be updated by transaction " + transactionId);
                }
            } else if (!record.canBeChangedByTransaction(transaction)) {
                throw new XdStorageException("object of " + clazz + " with idgeneration " + objectId + " can not be deleted by transaction " + transactionId);
            }

            if (record.isReadChange()) {
                record.setNewObject(null);
                record.markDelete(transaction);
                readObjects.remove(objectId);
            } else if (!record.isCommitedState() && record.isChangedByTransaction(transaction)) {
                if (record.isInsertChange()) {
                    cache.remove(objectId);
                    readObjects.remove(objectId);
                } else if (record.isUpdateChange()) {
                    record.setNewObject(null);
                    record.markDelete(transaction);
                    readObjects.remove(objectId);
                } else {
                    throw new XdStorageException("trying to double delete one object of " + clazz + " with idgeneration " + objectId);
                }
            } else if (record.canBeChangedByTransaction(transaction)) {
                record.setNewObject(null);
                record.markDelete(transaction);
                readObjects.remove(objectId);
            } else {
                throw new XdStorageException("concurrent modification one object of " + clazz + " with idgeneration " + objectId);
            }
        }

        Map<Object, CacheRecord> map = changes.get(transactionId);
        if (map == null) {
            changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            map = changes.get(transactionId);
        }
        if (map.put(objectId, record) == null) {
            undoLogOf(transactionId).addLast(record); // первая мутация записи — в журнал отката
        }
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
        undoLogByTransaction.remove(transactionId);
    }

    public void prepareCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        final String transactionId = transaction.getTransactionId();

        Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

        final Map<Object, CacheRecord> map = changes.get(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();
                Object oldObject = null, newObject = null;
                XdStorageObjectOperationType type = null;
                synchronized (record) {
                    if (record.isInsertChange()) {
                        newObject = record.getNewObject();
                        type = XdStorageObjectOperationType.Insert;

                        if (newObject != null) {
                            Object insertId = field.get(newObject);
                            if (insertId != null && readObjects != null && readObjects.containsKey(insertId)) {
                                newObject = readObjects.get(insertId).object;
                            }
                        }
                    } else if (record.isUpdateChange()) {
                        oldObject = record.getObject();
                        type = XdStorageObjectOperationType.Update;

                        if (oldObject != null) {
                            Object updateId = field.get(oldObject);
                            if (updateId != null && readObjects != null && readObjects.containsKey(updateId)) {
                                newObject = readObjects.get(updateId).object;
                            }
                        }
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

        Map<Object, ObjectChange> readObjects = readObjectsOfTransaction(transaction);

        final Map<Object, CacheRecord> map = changes.get(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();
                Object oldObject = null, newObject = null;
                XdStorageObjectOperationType type = null;
                synchronized (record) {
                    if (record.isInsertChange()) {
                        newObject = record.getNewObject();
                        type = XdStorageObjectOperationType.Insert;

                        if (newObject != null) {
                            Object commitId = field.get(newObject);
                            if (commitId != null && readObjects != null && readObjects.containsKey(commitId)) {
                                newObject = readObjects.get(commitId).object;
                            }
                        }
                    } else if (record.isUpdateChange()) {
                        oldObject = record.getObject();
                        type = XdStorageObjectOperationType.Update;

                        if (oldObject != null) {
                            Object commitId = field.get(oldObject);
                            if (commitId != null && readObjects != null && readObjects.containsKey(commitId)) {
                                newObject = readObjects.get(commitId).object;
                            }
                        }
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
        undoLogByTransaction.remove(transactionId); // изменения стали постоянными — журнал отката не нужен
        final Map<Object, CacheRecord> map = changes.remove(transactionId);
        if (map != null) {
            log.info("commit of tx {}: {} record(s) became permanent", transactionId, map.size());
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();

                if (log.isDebugEnabled()) {
                    log.debug("COMMIT by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                }

                // Фиксируем признак удаления строго по транзакционному контракту СУБД
//                boolean isDeleteOp = record.isDeleteChange() && record.isChangedByTransaction(transaction);
//                Object recordId = record.getId();

                synchronized (record) {
                    record.commit(transaction);
                }

//                if (isDeleteOp && recordId != null) {
//                    cache.remove(recordId);
//                }
                record.unlock(transaction);
            }
        }

        changesUnidentifiedObjectsByTransaction.remove(transactionId);
        readObjectsByTransaction.remove(transactionId);
    }

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        final Map<Object, CacheRecord> map = changes.remove(transactionId);
        final java.util.Deque<CacheRecord> undoLog = undoLogByTransaction.remove(transactionId);

        // UNDO-CHANGE-LOG: проигрываем журнал мутаций В ОБРАТНОМ порядке — каждая мутация
        // имеет ровно одну точку отмены, и откат строго инвертирует хронологию транзакции.
        if (undoLog != null) {
            final java.util.Iterator<CacheRecord> it = undoLog.descendingIterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next();

                if (log.isDebugEnabled()) {
                    log.debug("ROLLBACK by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                }

                // Фиксируем признак вставки строго по транзакционному контракту СУБД
                boolean isInsertOp = record.isInsertChange() && record.isChangedByTransaction(transaction);
                Object recordId = record.getId();

                try {
                    synchronized (record) {
                        record.rollback();
                    }

                    if (isInsertOp && recordId != null) {
                        cache.remove(recordId);
                    }
                    record.unlock(transaction);
                } catch (final RuntimeException e) {
                    // Откат не завершился: оставшиеся записи журнала содержат незакрытые мутации.
                    // Без этого ERROR половина отката была бы НЕЗаметной.
                    log.error("ROLLBACK FAILED mid-journal for transaction {} on record {} — {} undo entries left unprocessed",
                            transactionId, recordId, countRemaining(it), e);
                    throw e;
                }
            }
        } else if (map != null) {
            // Журнал отсутствует (транзакция из старого кода/мока) — прежний порядок по карте
            final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final CacheRecord record = it.next().getValue();
                boolean isInsertOp = record.isInsertChange() && record.isChangedByTransaction(transaction);
                Object recordId = record.getId();
                synchronized (record) {
                    record.rollback();
                }
                if (isInsertOp && recordId != null) {
                    cache.remove(recordId);
                }
                record.unlock(transaction);
            }
        }

        if (map != null || undoLog != null) {
            log.info("rollback of tx {}: {} record(s) reverted in reverse mutation order",
                    transactionId, map != null ? map.size() : 0);
        }

        changesUnidentifiedObjectsByTransaction.remove(transactionId);
        readObjectsByTransaction.remove(transactionId);
    }

    private static int countRemaining(final java.util.Iterator<?> it) {
        int n = 0;
        while (it.hasNext()) {
            it.next();
            ++n;
        }
        return n;
    }

    public void rollbackFailedCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> committed) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        undoLogByTransaction.remove(transactionId);
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

                    synchronized (record) {
                        record.rollback();
                    }
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
        record.transaction = transaction;
        record.change = Change.insert;
        record.timestamp = transaction.getTimestart();
        record.state = State.locked;
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

        /** Версия записи кэша на момент материализации (для RC-освежения). */
        public volatile long recordVersion = -1;

        public ObjectChange(final Object object, final Change change, final long readTimestamp) {
            this.object = object;
            this.change = change;
            this.readTimestamp = readTimestamp;
        }
    }

    private class CacheRecord {

        private Object id;

        private Object prevObject;

        private Object object;

        private Object newObject;

        private XdStorageTransaction transaction;

        private long timestamp = Long.MIN_VALUE;

        private long prevTimestamp = Long.MIN_VALUE;

        private Change change;

        private Change previousChange;

        private State state;

        private State previousState;

        private boolean firstPhaseCommit;

        private boolean secondPhaseCommit;

        private AtomicBoolean locked = new AtomicBoolean(false);

        /** Версия записи: растет при каждой мутации состояния (для RC-освежения чтений). */
        private volatile long version;

        /**
         * Номер фактического коммита текущей версии (граница видимости SNAPSHOT-читателей).
         * 0 = версия пришла с диска/еще не коммитилась в памяти — видна всем читателям.
         */
        private volatile long commitSerial;

        private CacheRecord() {
            // do nothing
        }

        public Object getId() {
            return this.id;
        }

        public long getVersion() {
            return version;
        }

        public boolean canBeChangedByTransaction(final XdStorageTransaction transaction) {
            boolean isOwnerTxAlive = this.transaction != null &&
                    services.getTransactionsManager().isTransactionAlive(this.transaction);

            // =========================================================================
            // ИСПРАВЛЕНИЕ: Если транзакция, удерживающая блокировку, мертва (!isOwnerTxAlive),
            // мы аннулируем замок, позволяя транзакции очистки беспрепятственно выполнить пурдж!
            // =========================================================================
            boolean result = this.state == State.undefined
                    || !isOwnerTxAlive
                    || (this.state == State.locked && this.transaction == transaction && transaction.getTimestart() == timestamp)
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
            boolean isOwnerTxAlive = this.transaction != null &&
                    services.getTransactionsManager().isTransactionAlive(this.transaction);

            boolean result = this.state == State.undefined
                    || !isOwnerTxAlive
                    || (this.state == State.locked && this.transaction == transaction && transaction.getTimestart() == timestamp)
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
            return this.change != Change.read && this.transaction != null && this.transaction == transaction
                    && transaction.getTimestart() == timestamp;
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
            // Было: return this.change == Change.insert; (но из-за опечаток в CHM проверялся Change.change)
            // Стало: Прямое, строгое и кристальное вычисление типа транзакционной вставки!
            return this.change == Change.insert;
        }

        public boolean isUpdateChange() {
            return this.change == Change.update;
        }

        public boolean isDeleteChange() {
            return this.change == Change.delete;
        }

        public void markUpdate(IXdStorageTransaction transaction) {
            ++version;
            this.prevObject = this.object;

            // =========================================================================
            // СУБД ТАЙМСТAМП-БAРЬEР: Защищаем историческую сетку времени при холодном старте!
            // Перезаписываем prevTimestamp только если у ноды есть реальное время коммита.
            // Это полностью предотвращает превращение timestamp в Long.MIN_VALUE при параллельных
            // конфликтах сессий, возвращая выборку к исходным 1000 элементам!
            // =========================================================================
            if (this.timestamp != Long.MIN_VALUE) {
                this.prevTimestamp = this.timestamp;
            }
            this.timestamp = transaction.getTimestart();

            this.previousChange = this.change;
            this.change = Change.update;
            this.previousState = this.state;
            this.state = State.locked;
        }

        public void markDelete(IXdStorageTransaction transaction) {
            ++version;
            this.prevObject = this.object;

            // Симметрично защищаем временную сетку для операций удаления
            if (this.timestamp != Long.MIN_VALUE) {
                this.prevTimestamp = this.timestamp;
            }
            this.timestamp = transaction.getTimestart();

            this.previousChange = this.change;
            this.change = Change.delete;
            this.previousState = this.state;
            this.state = State.locked;
        }

        private Map<String, Long> blockingTime = new HashMap<>();

        public void lock(final XdStorageTransaction transaction) throws XdStorageException {
            // R-27: инфраструктурные записи (деревья/узлы) НЕ захватывают latch. Структурная
            // конкуренция уже сериализована write-локом самого дерева; удержание latch-а до
            // конца транзакции-держателя на горячих узлах давало "locked for update" таймауты
            // при 25 воркерах. Своя-мутация определяется по record.transaction, проставленному
            // в createInsertRecord/markUpdate.
            if (infrastructureCache) {
                if (this.transaction == null) {
                    this.transaction = transaction;
                }
                return;
            }
            boolean isOwnerAlive = this.transaction != null &&
                    services.getTransactionsManager().isTransactionAlive(this.transaction);

            if (this.transaction != null && !isOwnerAlive && this.transaction != transaction) {
                this.transaction = null;
                synchronized (locked) {
                    locked.set(false);
                    locked.notify();
                }
            }

            if (this.transaction == null || this.transaction != transaction) {
                synchronized (locked) {
                    while (locked.get()) {
                        try {
                            final String transactionId = transaction.getTransactionId();

                            boolean currentOwnerAlive = this.transaction != null &&
                                    services.getTransactionsManager().isTransactionAlive(this.transaction);
                            if (this.transaction != null && !currentOwnerAlive) {
                                this.transaction = null;
                                locked.set(false);
                                break;
                            }

                            final Long startTime = blockingTime.remove(transactionId);
                            final Long currentTime = System.nanoTime();
                            if (startTime == null) {
                                blockingTime.put(transactionId, currentTime);
                            } else if ((currentTime - startTime) >= transaction.getTimeout() * 1_000_000) {
                                throw new XdStorageException("transaction " + transactionId + " should be rolled back by timeout. Object with id " + id + " is locked for " + change);
                            } else {
                                blockingTime.put(transactionId, startTime);
                            }

                            locked.wait(transaction.getTimeout() / 5);
                        } catch (final InterruptedException e) {
                            throw new XdStorageException("locking object interrupted", e);
                        }
                    }
                    locked.set(true);
                    // Phase D: запись о времени ожидания больше не нужна после захвата —
                    // иначе на записи течет по одному entry на каждую ждавшую транзакцию
                    blockingTime.remove(transaction.getTransactionId());
                }
                this.transaction = transaction;
            }
        }

        public void unlock(final XdStorageTransaction transaction) throws XdStorageException {
            // R-27: инфраструктурные записи не держат latch — просто разъединяем владельца
            if (infrastructureCache) {
                if (this.transaction == transaction) {
                    this.transaction = null;
                }
                return;
            }
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

        public void commit(IXdStorageTransaction tx) {
            ++version;
            if (!firstPhaseCommit || !secondPhaseCommit)
                throw new XdStorageRuntimeException("Illegal state of record to commit");

            this.prevObject = this.object;
            this.object = this.newObject;
            this.newObject = null;
            this.state = State.committed;
            this.previousState = State.undefined;

            firstPhaseCommit = false;
            secondPhaseCommit = false;

            this.prevTimestamp = this.timestamp;
            this.timestamp = tx.getTimestart();

            // НОМЕР ФАКТИЧЕСКОГО КОММИТА: граница видимости снапшотных читателей.
            // timestart владельца (метится при НАЧАЛЕ его транзакции) лжет читателям,
            // начавшим в окне [начало владельца, коммит]: версия еще не была видна,
            // когда они стартовали. Порядок по счетчику коммитов устраняет окно.
            this.commitSerial = XdStorageTransaction.nextCommitSerial();

            // =========================================================================
            // ФИНАЛЬНЫЙ АРХИТЕКТУРНЫЙ ШТРИХ: Свобождаем тип изменения ноды!
            // Мы обязаны сохранить историю в previousChange для старых транзакций,
            // но текущий маркер change переводим в Change.undefined, чтобы транзакции
            // из будущего могли повторно использовать этот ID для вставок (insert)!
            // =========================================================================
            this.previousChange = this.change;
            this.change = Change.undefined;
        }

        public void rollback() {
            this.object = this.prevObject;
            this.prevObject = null;
            this.newObject = null;
            this.state = this.previousState;
            this.previousState = State.undefined;
            this.change = previousChange;
            this.previousChange = Change.undefined;

            firstPhaseCommit = false;
            secondPhaseCommit = false;

            this.timestamp = this.prevTimestamp;
            this.prevTimestamp = Long.MIN_VALUE;
        }


        private Object getObjectForTransaction(XdStorageTransaction transaction) {
            Object object;
            if (isChangedByTransaction(transaction)) {
                // СОБСТВЕННЫЕ незакоммиченные мутации: newObject (null после своего delete = скрыто)
                object = this.newObject;
            } else if (servesCommittedVersion(transaction)) {
                // читатель зафиксировал снапшот ДО фактического коммита текущей версии — видит предыдущую
                object = this.prevObject;
            } else if (canBeChangedByTransaction(transaction)) {
                object = this.object;
            } else {
                object = this.prevObject;
            }
            return object;
        }

        /**
         * SNAPSHOT-гейт видимости версии: читатель, начавший транзакцию раньше фактического
         * коммита версии (порядок по счетчику коммитов, а не по часам), видит предыдущую версию.
         * Для мок-транзакций (без снапшот-номера) и незакоммиченных (locked) записей границей
         * остается timestart владельца. READ_COMMITTED: гейт выключен — всегда последнее
         * закоммиченное состояние.
         */
        private boolean servesCommittedVersion(XdStorageTransaction transaction) {
            if (isolationLevel == XdStorageIsolationLevel.READ_COMMITTED) {
                return false;
            }
            if (this.state == State.committed) {
                // 0 = «нет снапшот-номера» (мок-транзакции Mockito) — откат к timestart-сравнению
                final Long readerSerial = transaction.getSnapshotSerial();
                return readerSerial != null && readerSerial > 0
                        ? readerSerial < this.commitSerial
                        : transaction.getTimestart() < this.timestamp;
            }
            return transaction.getTimestart() < this.timestamp;
        }
    }

    /**
     * SNAPSHOT: читатель с timestart < версии видит предыдущую закоммиченную версию.
     * READ_COMMITTED: читатель всегда видит последнее закоммиченное состояние (record.getObject()).
     */
    private boolean servesSnapshotVersion(final XdStorageTransaction transaction, final CacheRecord record) {
        return isolationLevel != XdStorageIsolationLevel.READ_COMMITTED
                && transaction.getTimestart() < record.timestamp;
    }
}
