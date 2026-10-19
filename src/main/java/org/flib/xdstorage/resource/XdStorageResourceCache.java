package org.flib.xdstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.XdStorageConfig;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.resource.cache.XdStorageCacheChange;
import org.flib.xdstorage.resource.cache.XdStorageCacheCommitProtocol;
import org.flib.xdstorage.resource.cache.XdStorageCacheContext;
import org.flib.xdstorage.resource.cache.XdStorageCacheMutator;
import org.flib.xdstorage.resource.cache.XdStorageCacheObjectChange;
import org.flib.xdstorage.resource.cache.XdStorageCacheRecord;
import org.flib.xdstorage.resource.cache.XdStorageTransactionWorkspace;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Кэш ресурсов (Identity Map + MVCC-контуры транзакций) — ФАСАД читающих операций
 * и поддержки инфраструктуры B+Дерева. Разделение обязанностей (см. OPTIMIZATION-PLAN,
 * R-46): детали engine переехали в подпакет org.flib.xdstorage.resource.cache —
 * <ul>
 *   <li>{@link XdStorageCacheRecord} — MVCC-состояние записи, latch, видимость версий;</li>
 *   <li>{@link XdStorageTransactionWorkspace} — транзакционные контуры (write-set,
 *       журнал отката, неидентифицированные обертки, материализованное);</li>
 *   <li>{@link XdStorageCacheMutator} — контур мутаций insert/update/delete;</li>
 *   <li>{@link XdStorageCacheCommitProtocol} — 2PC-коммит, откат, откат упавшего коммита;</li>
 *   <li>{@link XdStorageCacheContext} — внутренний SPI фасада для коллабораторов.</li>
 * </ul>
 */
public class XdStorageResourceCache implements XdStorageCacheContext {

    private static final Logger log = LogManager.getLogger(XdStorageResourceCache.class);

    private final XdStorageServicesLocator services;

    private final IXdStorageCloner cloner;

    private final XdStorageObjectIdField field;

    /** Реестр записей кэша: id объекта -> MVCC-запись. */
    private final Map<Object, XdStorageCacheRecord> cache = new ConcurrentHashMap<>();

    /** Транзакционные контуры (write-set / журнал отката / неидентифицированные / прочитанное). */
    private final XdStorageTransactionWorkspace workspace = new XdStorageTransactionWorkspace();

    /** Контур мутаций (insert/update/delete). */
    private final XdStorageCacheMutator mutator = new XdStorageCacheMutator(this);

    /** Протокол завершения транзакции (2PC / rollback). */
    private final XdStorageCacheCommitProtocol commitProtocol = new XdStorageCacheCommitProtocol(this);

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
    /** R-42: ноды дерева — внутренняя структура с живым инстансом-истиной (полная инфра-релаксация). */
    private final boolean structuralNodeCache;

    public XdStorageResourceCache(final XdStorageServicesLocator services, final Class<?> clazz, final XdStorageObjectIdField field) {
        this.services = services;
        this.field = field;
        this.infrastructureCache = clazz != null && clazz.getName().contains("org.flib.xdstorage.btree");
        // R-42: НОДЫ — внутренняя структура (истина = живой инстанс, сериализация нодовыми
        // write-локами) — полная инфра-релаксация. Само ДЕРЕВО — логическая сущность БД:
        // контракты фантомов (BTreeEntityTest) действуют на его удаление.
        this.structuralNodeCache = clazz != null && clazz.getName().equals("org.flib.xdstorage.btree.XdStorageBTreeNode");
        this.clazz = clazz;
        this.cloner = this.services.getCloner();
        // O-3: уровень изоляции хранилища задается конфигом при открытии (раньше был
        // недостижим из публичного API); mock-локаторы в тестах остаются на SNAPSHOT
        final XdStorageConfig config = services.getStorageConfig();
        if (config != null && config.getIsolationLevel() != null) {
            this.isolationLevel = config.getIsolationLevel();
        }
    }

    // =========================================================================
    // ВНУТРЕННИЙ SPI (XdStorageCacheContext) — то, что коллабораторам из
    // подпакета cache нужно от фасада; состояние кэша остается приватным
    // =========================================================================

    @Override
    public XdStorageObjectIdField idField() {
        return field;
    }

    @Override
    public Map<Object, XdStorageCacheRecord> records() {
        return cache;
    }

    @Override
    public XdStorageTransactionWorkspace workspace() {
        return workspace;
    }

    @Override
    public boolean isInfrastructureCache() {
        return infrastructureCache;
    }

    @Override
    public boolean isStructuralNodeCache() {
        return structuralNodeCache;
    }

    @Override
    public Class<?> entityClass() {
        return clazz;
    }

    @Override
    public boolean isTransactionAlive(final XdStorageTransaction transaction) {
        return services.getTransactionsManager().isTransactionAlive(transaction);
    }

    @Override
    public XdStorageCacheRecord createReadRecord(final Object object) {
        return XdStorageCacheRecord.newReadRecord(this, field, object);
    }

    @Override
    public XdStorageCacheRecord createInsertRecord(final Object newObject, final XdStorageTransaction transaction) {
        return XdStorageCacheRecord.newInsertRecord(this, field, newObject, transaction);
    }

    // =========================================================================
    // КОНФИГУРАЦИЯ / СЧЕТЧИКИ
    // =========================================================================

    @Override
    public XdStorageIsolationLevel getIsolationLevel() {
        return isolationLevel;
    }

    public void setIsolationLevel(final XdStorageIsolationLevel isolationLevel) {
        this.isolationLevel = isolationLevel == null ? XdStorageIsolationLevel.SNAPSHOT : isolationLevel;
    }

    public long getObjectsCount() {
        return cache.size();
    }

    public boolean hasObject(final Object id) {
        return cache.containsKey(id);
    }

    // =========================================================================
    // НАПОЛНЕНИЕ КЭША (загрузка ресурса с диска)
    // =========================================================================

    public void fillCache(final Collection<Object> objects) {
        for (final Object object : objects) {
            // ФАЗА 3B: parse-копия уступает место живому каноническому инстансу (если он есть)
            final Object objectCanonical = adoptCanonicalInstance(object);
            cache.putIfAbsent(field.get(objectCanonical), createReadRecord(objectCanonical));
        }
    }

    /** W-1: replay of a committed journal UPSERT — base-парсинг уступает состоянию журнала (last writer wins), поэтому put, не putIfAbsent. */
    public void applyCommittedJournalUpsert(final Object object) {
        final Object objectCanonical = adoptCanonicalInstance(object);
        cache.put(field.get(objectCanonical), createReadRecord(objectCanonical));
    }

    /** W-1: replay of a committed journal DELETE. */
    public void applyCommittedJournalDelete(final Object id) {
        cache.remove(id);
    }

    /**
     * W-2: per-transaction materialization budget (XdStorageConfig.txMaterializedObjectsCap,
     * 0 = unlimited). Checked at the exit of every READ-path materialization; WRITE_OUT
     * (коммитная выписка) сознательно не бюджетируется. Превышение — громкий отказ с
     * подсказкой вместо молчаливого роста кучи до OOM.
     */
    /** W-2: бюджет включают ТОЛЬКО ресурсы пользовательских данных (id-индексы — служебные записи, не бюджетируются). */
    private volatile boolean enforceMaterializationBudget;

    public void setEnforceMaterializationBudget(final boolean value) {
        this.enforceMaterializationBudget = value;
    }

    private void checkMaterializationBudget(final XdStorageTransaction transaction,
                                            final Map<Object, XdStorageCacheObjectChange> readObjects)
            throws XdStorageException {
        if (!enforceMaterializationBudget || readObjects == null) {
            return;
        }
        final XdStorageConfig config = services.getStorageConfig();
        final long cap = config != null ? config.getTxMaterializedObjectsCap() : 0L;
        if (cap > 0 && readObjects.size() > cap) {
            throw new XdStorageException("transaction " + transaction.getTransactionId()
                    + " materialization budget exceeded: " + readObjects.size() + " materialized objects > cap " + cap
                    + " — narrow the query (predicate / index search / watch) or raise txMaterializedObjectsCap");
        }
    }

    public <T> void fillCacheOnlyIfNotExists(final Collection<T> objects) {
        for (final Object object : objects) {
            final Object objectCanonical = adoptCanonicalInstance(object);
            cache.putIfAbsent(field.get(objectCanonical), createReadRecord(objectCanonical));
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

    @SuppressWarnings("unchecked")
    private <T> void fillAndCloneCacheOnlyIfNotExistsInternal(final List<T> objects, final Collection<T> result, final XdStorageTransaction transaction) throws XdStorageException {
        log.debug("fillAndClone: {} object(s) into the read contour of tx {}", objects.size(), transaction.getTransactionId());
        final Map<Object, XdStorageCacheObjectChange> tmpReadObjects = workspace.readObjectsOf(transaction);

        for (final T object : objects) {
            final Object objectCanonical = adoptCanonicalInstance(object);
            final Object objectId = field.get(objectCanonical);
            final Object objectToCache = objectCanonical;
            final Object clonedObject = infrastructureCache
                    ? objectToCache
                    : cloner.cloneAndWrap(objectToCache, services.getStorage(), transaction);
            tmpReadObjects.putIfAbsent(objectId, new XdStorageCacheObjectChange(clonedObject, XdStorageCacheChange.read, System.nanoTime()));
            result.add((T) tmpReadObjects.get(objectId).object);
            cache.putIfAbsent(objectId, createReadRecord(objectToCache));
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
        // R-53: парсерные стабы (references.yaml/readReference -> newInstance с одним id)
        // НЕ канонизируются: references-ресурс инфраструктурного класса готовится раньше
        // по-id объектных файлов, и зарегистрированный стаб навсегда подменял собой
        // канонический инстанс (реальный парс затем отбрасывался как LOST-TO-EXISTING) —
        // materialize() получал пустой узел. Реестр держит только живые/полные инстансы.
        if (parsed instanceof org.flib.xdstorage.code.IXdStorageReferenceMarked
                && ((org.flib.xdstorage.code.IXdStorageReferenceMarked) parsed).isReference()) {
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

    // =========================================================================
    // НЕИДЕНТИФИЦИРОВАННЫЕ ОБЪЕКТЫ (observable-обертки без DB-id)
    // =========================================================================

    public <T> T findUnidentified(final Object object, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            return findUnidentifiedInternal(object, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T findUnidentifiedInternal(final Object object, final XdStorageTransaction transaction) {
        if (IXdStorageIdObservableWrapper.class.isAssignableFrom(object.getClass())) {
            final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                    workspace.unidentified(transaction.getTransactionId());
            if (unidentifiedObjects != null) {

                return (T) unidentifiedObjects.get(object);
            }
        }
        return null;
    }

    // =========================================================================
    // СКАНИРУЮЩИЕ ЧТЕНИЯ (предикат / полный / unsafe)
    // =========================================================================

    @SuppressWarnings("unchecked")
    public <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            final Collection<T> result = readInternal(transaction, predicate);
            checkMaterializationBudget(transaction, workspace.readObjectsOf(transaction));
            return result;
        } finally {
            transaction.finishCriticalSection();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> readInternal(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException {
        final XdStorageException[] exceptions = new XdStorageException[]{null};
        final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);

        Map<Object, XdStorageCacheObjectChange> tmpReadObjects = workspace.readObjectsOf(transaction);
        final Map<Object, XdStorageCacheObjectChange> readObjects = tmpReadObjects;

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
            XdStorageCacheRecord record = (XdStorageCacheRecord) node;

            Object object = null;
            synchronized (record) {
                object = record.resolveVisible(transaction, XdStorageCacheRecord.View.SCAN, 0L);
            }

            if (object != null) {
                try {
                    final Object objectId = field.get(object);
                    // Phase D-1: в READ COMMITTED перечитываем свежую копию ТОЛЬКО когда версия
                    // записи изменилась с момента материализации (чужой mark/commit) или это своя
                    // мутация. Раньше каждый скан переклонировал ВСЕ строки — O(N) клонов на скан.
                    final boolean rcRefresh = isolationLevel == XdStorageIsolationLevel.READ_COMMITTED;
                    final XdStorageCacheObjectChange materialized = readObjects.get(objectId);
                    final boolean needsRematerialize = materialized == null
                            || record.isChangedByTransaction(transaction)
                            || (rcRefresh && materialized.recordVersion != record.getVersion());
                    if (needsRematerialize) {
                        Object cloned = cloner.cloneAndWrap(object, services.getStorage(), transaction);
                        final XdStorageCacheObjectChange oc = new XdStorageCacheObjectChange(cloned, XdStorageCacheChange.read, System.nanoTime());
                        oc.recordVersion = record.getVersion();
                        if (rcRefresh) {
                            readObjects.put(objectId, oc);
                        } else {
                            readObjects.putIfAbsent(objectId, oc);
                        }
                    }
                    Object objectToReturn = readObjects.get(objectId).object;

                    // =========================================================================
                    // СУБД-БАРЬEР БEЗOПAСНOСТИ ПРEДИКАТА: Если объект не удалось обернуть в
                    // unmodifiable-прокси (wrapAsUnmodifiableObject вернул null), мы ОБЯЗАНЫ
                    // проверить предикат по сырому материализованному объекту, а не выбрасывать
                    // объект из выборки. Иначе диапазонное чтение молча теряет записи, которые
                    // видит полный скан read(transaction) — нарушение контракта Snapshot Isolation!
                    // =========================================================================
                    if (objectToReturn != null) {
                        final Object wrappedObject = XdStorageObjectUtils.wrapAsUnmodifiableObject(objectToReturn, services.getStorage(), transaction);
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
                workspace.unidentified(transaction.getTransactionId());
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
            checkMaterializationBudget(transaction, workspace.readObjectsOf(transaction));
        } finally {
            transaction.finishCriticalSection();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void watchInternal(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException {
        final XdStorageException[] exceptions = new XdStorageException[]{null};
        final AtomicBoolean hasError = new AtomicBoolean(false);

        Map<Object, XdStorageCacheObjectChange> tmpReadObjects = workspace.readObjectsOf(transaction);
        final Map<Object, XdStorageCacheObjectChange> readObjects = tmpReadObjects;

        // =========================================================================
        // СНАЙПEРСКИЙ ФИКС CLASSCASTEXCEPTION: Снимаем чистый Snapshot-массив Object[]!
        // Избавляемся от хрупкого принудительного каста (CacheRecord[]) всего массива.
        // =========================================================================
        Object[] recordsSnapshot = cache.values().toArray();

        Arrays.stream(recordsSnapshot).forEach(node -> {
            if (hasError.get() || node == null) {
                return;
            }
            XdStorageCacheRecord record = (XdStorageCacheRecord) node;

            Object object = null;
            synchronized (record) {
                object = record.resolveVisible(transaction, XdStorageCacheRecord.View.SCAN, 0L);
            }

            try {
                if (object != null) {
                    final Object objectId = field.get(object);
                    // READ COMMITTED: если чужой коммит изменил запись после материализации —
                    // сбрасываем устаревшую копию, ниже она пересоздастся (как в readInternal(id))
                    final XdStorageCacheObjectChange materialized = readObjects.get(objectId);
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
                            final XdStorageCacheObjectChange refreshed = new XdStorageCacheObjectChange(objectToWrap, XdStorageCacheChange.read, System.nanoTime());
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

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                workspace.unidentified(transaction.getTransactionId());
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

    @SuppressWarnings("unchecked")
    public <T> Collection<T> read(final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            final Collection<T> result = readInternal(transaction);
            checkMaterializationBudget(transaction, workspace.readObjectsOf(transaction));
            return result;
        } finally {
            transaction.finishCriticalSection();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Collection<T> readInternal(final XdStorageTransaction transaction) throws XdStorageException {
        Map<Object, XdStorageCacheObjectChange> readObjects = workspace.readObjectsOf(transaction);

        final List<T> result = Collections.synchronizedList(new ArrayList<>());

        // БЛOК 1: СУБД SNAPSHOT VIEW БАРЬЕР (Полный скан кэша)
        Object[] recordsSnapshot = cache.values().toArray();

        for (final Object node : recordsSnapshot) {
            if (node == null) continue;
            XdStorageCacheRecord record = (XdStorageCacheRecord) node;

            Object object = null;
            synchronized (record) {
                object = record.resolveVisible(transaction, XdStorageCacheRecord.View.SCAN, 0L);
            }

            if (object != null) {
                final Object objectId = field.get(object);
                synchronized (result) {
                    synchronized (record) {
                        // Phase D-1: в RC перечитываем ТОЛЬКО при изменившейся версии записи
                        // (или своя мутация); SNAPSHOT — материализация при отсутствии, как раньше
                        final XdStorageCacheObjectChange materialized = readObjects.get(objectId);
                        final boolean needsRematerialize = materialized == null
                                || record.isChangedByTransaction(transaction)
                                || (isolationLevel == XdStorageIsolationLevel.READ_COMMITTED
                                    && materialized.recordVersion != record.getVersion());
                        if (needsRematerialize) {
                            Object cloned = cloner.cloneAndWrap(object, services.getStorage(), transaction);
                            final XdStorageCacheObjectChange oc = new XdStorageCacheObjectChange(cloned, XdStorageCacheChange.read, System.nanoTime());
                            oc.recordVersion = record.getVersion();
                            readObjects.put(objectId, oc);
                        }
                    }
                    result.add((T) readObjects.get(objectId).object);
                }
            }
        }

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                workspace.unidentified(transaction.getTransactionId());
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

    @SuppressWarnings("unchecked")
    private <T> Collection<T> readUnsafeInternal(final XdStorageTransaction transaction) {
        Map<Object, XdStorageCacheObjectChange> readObjects = workspace.readObjectsOf(transaction);

        final List<T> result = Collections.synchronizedList(new ArrayList<>());

        // БЛOК 1: СУБД SNAPSHOT VIEW БАРЬЕР (Unsafe скан)
        Object[] recordsSnapshot = cache.values().toArray();

        for (final Object node : recordsSnapshot) {
            if (node == null) continue;
            XdStorageCacheRecord record = (XdStorageCacheRecord) node;

            Object object = null;
            synchronized (record) {
                object = record.resolveVisible(transaction, XdStorageCacheRecord.View.WRITE_OUT, 0L);
            }
            if (object != null) {
                final Object objectId = field.get(object);
                final XdStorageCacheObjectChange readObject = readObjects.get(objectId);
                if (readObject != null && readObject.change != XdStorageCacheChange.read) {
                    result.add((T) readObject.object);
                } else {
                    result.add((T) object);
                }
            }
        }

        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects =
                workspace.unidentified(transaction.getTransactionId());
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

    // =========================================================================
    // ЧТЕНИЕ ПО ID И ПО ССЫЛКЕ
    // =========================================================================

    public Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            final Object result = readInternal(id, transaction);
            checkMaterializationBudget(transaction, workspace.readObjectsOf(transaction));
            return result;
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private Object readInternal(final Object id, final XdStorageTransaction transaction) throws XdStorageException {
        Map<Object, XdStorageCacheObjectChange> tmpReadObjects = workspace.readObjectsOf(transaction);
        final Map<Object, XdStorageCacheObjectChange> readObjects = tmpReadObjects;

        XdStorageCacheRecord record = cache.get(id);

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
                log.warn("cache record for infra object {}#{} was lost — restored from the canonical registry",
                        clazz.getSimpleName(), id);
                cache.putIfAbsent(id, createReadRecord(canonical));
                record = cache.get(id);
            }
        }
        XdStorageCacheObjectChange materialized = readObjects.get(id);

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
                    readObjects.putIfAbsent(id, new XdStorageCacheObjectChange(object, XdStorageCacheChange.read, System.nanoTime()));
                    return readObjects.get(id).object;
                }
                if (Boolean.getBoolean("xdstorage.chain.debug")) {
                    synchronized (record) {
                        System.out.println("[CHAIN] INFRA-NULL id=" + id + " tx=" + transaction.getTransactionId()
                                + " state=" + record.getState() + " own=" + record.isChangedByTransaction(transaction)
                                + " owner=" + record.getOwnerTransactionId());
                    }
                }
                return null;
            }

            // Канонический MVCC-барьер вычисления видимости в оперативной памяти
            synchronized (record) {
                // R-45: если запись удерживает ЧУЖАЯ транзакция, находящаяся В ФАЗЕ КОММИТА,
                // и снапшот читателя уже включает ее единый commit-serial — читатель обязан
                // видеть НОВОЕ состояние (все эффекты коммита атомарно), несмотря на то что
                // фаза-2 этой конкретной записи еще не дошла до нее. Без этого читатель между
                // фазами разных ресурсов видел новую систему со ссылками на еще "невидимые"
                // планеты ("cannot load by reference") — дыра атомарности коммита GraphPhantom.
                final long committingSerial = record.committingSerialVisibleTo(transaction);
                object = record.resolveVisible(transaction, XdStorageCacheRecord.View.BY_ID, committingSerial);

                if (object != null) {
                    if (!readObjects.containsKey(id) || record.isChangedByTransaction(transaction)) {
                        Object cloned = cloner.cloneAndWrap(object, services.getStorage(), transaction);
                        final XdStorageCacheObjectChange oc = new XdStorageCacheObjectChange(cloned, XdStorageCacheChange.read, System.nanoTime());
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
            checkMaterializationBudget(transaction, workspace.readObjectsOf(transaction));
        } finally {
            transaction.finishCriticalSection();
        }
    }

    private void readByReferenceInternal(final Object reference, final XdStorageTransaction transaction) throws XdStorageException {
        Map<Object, XdStorageCacheObjectChange> readObjects = workspace.readObjectsOf(transaction);

        final Object objectId = field.get(reference);
        if (objectId == null) {
            return;
        }

        XdStorageCacheRecord record = cache.get(objectId);

        // ФАЗА 3B: записи нет — восстанавливаем из реестра канонических инстансов (см. readInternal)
        if (infrastructureCache && record == null) {
            final XdStorageCanonicalInstanceRegistry registry = services.getCanonicalInstances();
            final Object canonical = registry != null ? registry.get(clazz, objectId) : null;
            if (canonical != null) {
                log.warn("cache record for infra object {}#{} was lost — restored from the canonical registry",
                        clazz.getSimpleName(), objectId);
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
                    readObjects.putIfAbsent(objectId, new XdStorageCacheObjectChange(reference, XdStorageCacheChange.read, System.nanoTime()));
                    return;
                }
                throw new XdStorageException("cannot load by reference object of " + reference.getClass() + " with idgeneration " + field.get(reference));
            }
            synchronized (record) {
                object = record.resolveVisible(transaction, XdStorageCacheRecord.View.SCAN, 0L);

                if (object != null) {
                    // READ COMMITTED: наполняем ссылку последним закоммиченным состоянием,
                    // а не устаревшей материализацией из транзитной карты
                    cloner.fillAndWrap(reference, object, services.getStorage(), transaction);
                    readObjects.putIfAbsent(objectId, new XdStorageCacheObjectChange(reference, XdStorageCacheChange.read, System.nanoTime()));
                    return;
                }
            }
        }

        throw new XdStorageException("cannot load by reference object of " + reference.getClass() + " with idgeneration " + field.get(reference));
    }

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
    private boolean waitForRecordResolution(final XdStorageCacheRecord initialRecord, final Object objectId,
                                            final XdStorageTransaction transaction) throws XdStorageException {
        if (!isInvisibleUncommittedMutation(initialRecord, transaction)) {
            return false;
        }
        final long timeout = Math.min(transaction.getTimeout() > 0 ? transaction.getTimeout() : 5000L, 3000L);
        final long deadlineNano = System.nanoTime() + timeout * 1_000_000L;
        XdStorageCacheRecord record = initialRecord;

        while (isInvisibleUncommittedMutation(record, transaction)) {
            if (System.nanoTime() >= deadlineNano) {
                // Чужая транзакция держит инфраструктурную запись невидимой дольше бюджета:
                // читатель идет дальше с "честным отказом" — этот факт обязан быть в логе.
                log.warn("infra record {} stayed invisible past the wait budget ({} ms, owner tx: {}) — serving absent",
                        objectId, timeout, record.getOwnerTransactionId());
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
                log.debug("infra record {} disappeared while waiting — owner rolled back the invisible insert", objectId);
                return false; // запись изъята (откат вставки) — записи больше нет
            }
        }
        return true;
    }

    private boolean isInvisibleUncommittedMutation(final XdStorageCacheRecord record, final XdStorageTransaction transaction) {
        if (record == null || record.isCommitedState()) {
            return false;
        }
        if (record.isChangedByTransaction(transaction)) {
            return false; // своя мутация — видим через newObject
        }
        if (!record.isInsertChange() && !record.isUpdateChange()) {
            return false; // delete/read-состояния обрабатываются обычным MVCC-переключателем
        }
        return services.getTransactionsManager().isTransactionAlive(record.getOwnerTransaction());
    }

    // =========================================================================
    // ПОДДЕРЖКА ИНФРАСТРУКТУРЫ B+ДЕРЕВА (живые канонические инстансы)
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
    @Override
    public Object materializeForCache(final Object newObject) throws XdStorageException {
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

    // =========================================================================
    // МУТАЦИИ (делегация контуру XdStorageCacheMutator)
    // =========================================================================

    public void insert(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            final String transactionId = transaction.getTransactionId();

            // ГЛОБАЛЬНОЕ ИСПРАВЛЕНИЕ СУБД: Защита от избыточных повторных вставок.
            // Если объект уже имеет ID и зарегистрирован в карте изменений текущей транзакции,
            // мы fail-safe завершаем метод, предотвращая коллизии MVCC-версий на индексах классов!
            if (!IXdStorageIdObservableWrapper.class.isAssignableFrom(newObject.getClass())) {
                final Object objectId = field.get(newObject);
                final Map<Object, XdStorageCacheRecord> map = workspace.changes(transactionId);
                if (objectId != null && map != null && map.containsKey(objectId)) {
                    final XdStorageCacheRecord existingRecord = map.get(objectId);
                    if (existingRecord.isInsertChange() || existingRecord.isUpdateChange()) {
                        return; // Объект уже находится в транзакционном контуре, игнорируем дубликат
                    }
                }
            }

            mutator.insertInternal(newObject, transaction);
        } finally {
            transaction.finishCriticalSection();
        }
    }

    public void update(final Object newObject, final XdStorageTransaction transaction) throws XdStorageException {
        mutator.update(newObject, transaction);
    }

    public void delete(final Object objectId, final XdStorageTransaction transaction) throws XdStorageException {
        mutator.delete(objectId, transaction);
    }

    // =========================================================================
    // ЗАВЕРШЕНИЕ ТРАНЗАКЦИИ (делегация XdStorageCacheCommitProtocol)
    // =========================================================================

    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
        transaction.startCriticalSection();
        try {
            return workspace.hasChanges(transaction.getTransactionId());
        } finally {
            transaction.finishCriticalSection();
        }
    }

    public void clear(final XdStorageTransaction transaction) {
        workspace.clear(transaction.getTransactionId());
    }

    public void prepareCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        commitProtocol.prepareCommit(transaction, collector);
    }

    public void performCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        commitProtocol.performCommit(transaction, collector);
    }

    public void commit(final XdStorageTransaction transaction) throws XdStorageException {
        commitProtocol.commit(transaction);
    }

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        commitProtocol.rollback(transaction);
    }

    public void rollbackFailedCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> committed) throws XdStorageException {
        commitProtocol.rollbackFailedCommit(transaction, committed);
    }
}
