package io.github.xdstorage.btree;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.lock.XdStorageReadWriteLock;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
public class XdStorageBTree implements IXdStorageBTreeNode, io.github.xdstorage.serialization.IXdStorageSerializationGuard {

    private static final Logger log = LoggerFactory.getLogger(XdStorageBTree.class);

    /** R-36→R-54: бюджет ретраев спуска поиска — по ВРЕМЕНИ (см. find), не по попыткам.
     *  Верхняя граница дедлайна: даже при большом таймауте транзакции читатель не hangs дольше 10с. */
    private static final long FIND_RETRY_BUDGET_CAP_MS = 10_000;
    /** R-54: потолок экспоненциального backoff между ретраями поиска. */
    private static final long FIND_RETRY_BACKOFF_MAX_MILLIS = 25;

    /** R-38: страховка предпрогрева цепи листьев от зацикливания (штатная цепь конечна). */
    private static final int PREWARM_STEP_BUDGET = 1_000_000;

    @XdStorageObjectId
    private XdStorageBTreeId id;

    private boolean isReference = true;

    private boolean multiple;

    private int t;

    private XdStorageBTreeNode root;

    private XdStorageBTreeNode firstLeaf;

    // Маркер жизненного цикла: фиксирует, что дерево хотя бы раз инициализировало структуру узлов.
    // Позволяет надежно отличать первичную пустую базу от базы, опустошенной в ходе транзакции очистки!
    private boolean wasInitialized = false;

    public XdStorageBTree() {
        // do nothing
    }

    public XdStorageBTree(final XdStorageBTreeId id, final boolean multiple, final int t) {
        this.id = id;
        this.isReference = false;
        this.multiple = multiple;
        this.t = t;

        if (t <= 1) {
            throw new XdStorageRuntimeException("Parameter t of BTree cannot be letter or equal 1");
        }
    }

    private final XdStorageReadWriteLock lock = new XdStorageReadWriteLock();

    // R-56: детектор структурных мутаций. structureVersion инкрементируется на входе в
    // каждую мутацию (insert/delete/update), activeMutations считает мутации «в полете».
    // Валидатор сэмплирует пару (version, active) до/между/после двух своих обходов:
    // изменение версии или активные мутации означают, что наблюдение не атомарно
    // (канал параллельной мутации), и результат валидации обязан быть отброшен/повторен,
    // а не превращен в громкий отказ («рассинхрон структуры»).
    private final AtomicLong structureVersion = new AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger activeMutations = new java.util.concurrent.atomic.AtomicInteger();
    /** R-56 диагностика: кто именно внутри мутационной секции (поток → описание операции). */
    private final java.util.concurrent.ConcurrentHashMap<Thread, String> activeMutatorThreads =
            new java.util.concurrent.ConcurrentHashMap<>();

    public long currentStructureVersion() {
        return structureVersion.get();
    }

    public int activeMutationCount() {
        return activeMutations.get();
    }

    /** R-56: снимок активных мутаторов для диагностики сигналов валидатора. */
    public String describeActiveMutators() {
        return activeMutatorThreads.toString();
    }

    // БЮДЖЕТ ПОПЫТОК РЕБАЛАНСИРОВКИ: retry-циклы операций обязаны быть ограничены —
    // иначе устойчивая конкуренция за deep-локи превращает их в вечный livelock,
    // вешающий потоки (дефект зависания cleanShutdown в merge-стрессе).
    private static final int REBALANCE_RETRY_BUDGET = 10;

    @Override
    public boolean isWriteLocked() {
        return lock.isWriteLocked();
    }

    // R-43: сериализация дерева (root/firstLeaf) — tryLock-версия, см. ноду.
    @Override
    public void beginConsistentSerialization() {
        tryLockWrite(null);
    }

    @Override
    public void endConsistentSerialization() {
        if (isWriteLocked()) {
            unlockWrite();
        }
    }

    @Override
    public boolean isReadLocked() {
        return lock.isReadLocked();
    }

    @Override
    public void lockWrite(final IXdStorageTransaction transaction) throws XdStorageException {
        try {
            lock.lockWrite(transaction);
        } catch (final InterruptedException e) {
            throw new XdStorageException(e);
        }
    }

    @Override
    public void lockRead(final IXdStorageTransaction transaction) throws XdStorageException {
        try {
            lock.lockRead(transaction);
        } catch (final InterruptedException e) {
            throw new XdStorageException(e);
        }
    }

    @Override
    public boolean tryLockWrite(final IXdStorageTransaction transaction) {
        return lock.tryLockWrite();
    }

    @Override
    public boolean tryLockRead(final IXdStorageTransaction transaction) {
        return lock.tryLockRead();
    }

    @Override
    public void unlockWrite() {
        lock.unlockWrite();
    }

    @Override
    public void unlockRead() {
        lock.unlockRead();
    }

    public XdStorageBTreeId getId() {
        return id;
    }

    public void setId(final XdStorageBTreeId id) {
        this.id = id;
    }

    public boolean isReference() {
        return isReference;
    }

    public void setReference(boolean reference) {
        isReference = reference;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    public int getT() {
        return t;
    }

    public void setT(final int t) {
        if (t <= 1) {
            throw new XdStorageRuntimeException("Parameter t of BTree cannot be letter or equal 1");
        }
        this.t = t;
    }

    public XdStorageBTreeNode getRoot() {
        return root;
    }

    public void setRoot(final XdStorageBTreeNode root) {
        this.root = root;
    }

    public XdStorageBTreeNode getFirstLeaf() {
        return firstLeaf;
    }

    public void setFirstLeaf(final XdStorageBTreeNode firstLeaf) {
        this.firstLeaf = firstLeaf;
    }

    public void insert(final Comparable key, final Object object,
                       final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        // ИСПРАВЛЕНИЕ: Fail-fast проверка прерывания потока на входе в СУБД
        if (Thread.currentThread().isInterrupted()) {
            throw new XdStorageException("interrupted", new InterruptedException("Поток прерван перед вставкой в B+ Дерево"));
        }

        lockWrite(transaction);
        try {
            if (root == null) {
                createRoot(storage, transaction);
            }

            // R-53: парсерный стаб корня (холодное открытие) заменяется каноническим
            // инстансом — иначе спуск идет по пустому узлу и вставки после реопена
            // портят структуру (PERFORMANCE-RECOMMENDATIONS.md §6);
            // firstLeaf — тоже: цепочка листьев нужна валидатору и сканам
            root = this.root = XdStorageBTreeNode.materialize(root, storage, transaction, this, null);
            if (firstLeaf != null) {
                firstLeaf = this.firstLeaf = XdStorageBTreeNode.materialize(firstLeaf, storage, transaction, this, null);
            }
        } finally {
            unlockWrite();
        }

        final AtomicLong counter = new AtomicLong(0);
        final AtomicBoolean retryInsert = new AtomicBoolean();
        int attempts = 0;
        do {
            synchronized (counter) {
                if (counter.incrementAndGet() % 5 == 0) {
                    try {
                        counter.wait(50);
                    } catch (InterruptedException e) {
                        throw new XdStorageException("interrupted", e);
                    }
                }
            }
            if (++attempts > REBALANCE_RETRY_BUDGET) {
                log.warn("B+Tree rebalance retry budget exhausted for insert of key {} in tree {} — sustained rebalance lock contention (operation failed loudly)", key,
                        id);
                throw new XdStorageException("retry budget exhausted by insert of idgeneration " + key
                        + " into " + id + ": устойчивая конкуренция за блокировки ребалансировки");
            }

            retryInsert.set(false);
            lockWrite(transaction);
            try {
                if (root == null) {
                    createRoot(storage, transaction);
                }
                // R-56: счетчики ТОЛЬКО внутри lockWrite-секции — иначе они флагают
                // ждущих мутаторов (версия растет у заблокированного на входе воркера),
                // а не структурные изменения.
                structureVersion.incrementAndGet();
                activeMutations.incrementAndGet();
                activeMutatorThreads.put(Thread.currentThread(), "insert");
                try {
                    root.insert(this, key, object, storage, transaction, retryInsert);
                } finally {
                    activeMutatorThreads.remove(Thread.currentThread());
                    activeMutations.decrementAndGet();
                }
            } finally {
                // R-33: страховка утечки write-лока при throw из write-секции. deepUnlock
                // внутри node-операций уже снял глубокую цепь (включая tree); разблокируем
                // только если loop-уровневый захват еще держится текущим потоком.
                if (isWriteLocked()) {
                    unlockWrite();
                }
            }
        } while (retryInsert.get());
    }

    private void createRoot(final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        log.debug("B+Tree {} created root (first key stored)", id);
        firstLeaf = root = new XdStorageBTreeNode(this, null);
        this.wasInitialized = true; // Фиксируем факт рождения структуры Б+ Дерева
        storage.save(root, transaction);
        storage.update(this, transaction);
    }

    public void delete(final Comparable key, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (Thread.currentThread().isInterrupted()) {
            throw new XdStorageException("interrupted", new InterruptedException("Поток прерван перед удалением из B+ Дерева"));
        }

        lockWrite(transaction);
        try {
            // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Корень дефекта 65 / 88):
            // Если поле root вернуло null, но маркер wasInitialized равен true, это означает,
            // что старый родитель был удален каскадным сжатием ярусов join, а новый корень
            // находится в транзакционном кэше изменений сессии.
            // Перегружаем состояние bTree из storage, чтобы восстановить живую ссылку на root!
            if (root == null && wasInitialized) {
                XdStorageBTree freshTree = storage.load(XdStorageBTree.class, this.id, transaction);
                if (freshTree != null && freshTree.getRoot() != null) {
                    this.root = freshTree.getRoot();
                    this.firstLeaf = freshTree.getFirstLeaf();
                }
            }

            if (root != null) {
                // R-53: парсерный стаб корня (холодное открытие) заменяется каноническим
                // инстансом — удаления после реопена обязаны идти по живому графу;
                // firstLeaf — тоже (цепочка листьев: валидатор, слияния листьев)
                root = this.root = XdStorageBTreeNode.materialize(root, storage, transaction, this, null);
                if (firstLeaf != null) {
                    firstLeaf = this.firstLeaf = XdStorageBTreeNode.materialize(firstLeaf, storage, transaction, this, null);
                }
            } else {
                if (!wasInitialized && !isReference) {
                    throw new XdStorageException("object of " + (id != null ? id.getCl().getName() : "Unknown") + " with idgeneration " + key + " does not exists");
                }
                return;
            }
        } finally {
            unlockWrite();
        }

        final AtomicLong counter = new AtomicLong(0);
        final AtomicBoolean retryDelete = new AtomicBoolean();
        int attempts = 0;
        do {
            synchronized (counter) {
                if (counter.incrementAndGet() % 5 == 0) {
                    try {
                        counter.wait(50);
                    } catch (InterruptedException e) {
                        throw new XdStorageException("interrupted", e);
                    }
                }
            }
            if (++attempts > REBALANCE_RETRY_BUDGET) {
                log.warn("B+Tree rebalance retry budget exhausted for delete of key {} in tree {} — sustained rebalance lock contention (operation failed loudly)", key,
                        id);
                throw new XdStorageException("retry budget exhausted by delete of idgeneration " + key
                        + " from " + id + ": устойчивая конкуренция за блокировки ребалансировки");
            }

            retryDelete.set(false);
            lockWrite(transaction);
            try {
                // Защита от фантомного зануления внутриdo-while цикла
                if (root == null && wasInitialized) {
                    XdStorageBTree freshTree = storage.load(XdStorageBTree.class, this.id, transaction);
                    if (freshTree != null && freshTree.getRoot() != null) {
                        this.root = freshTree.getRoot();
                        this.firstLeaf = freshTree.getFirstLeaf();
                    }
                }

                if (root == null) {
                    log.debug("B+Tree {} is empty after delete of key {} (root purged)", id, key);
                    return;
                }
                structureVersion.incrementAndGet();
                activeMutations.incrementAndGet();
                activeMutatorThreads.put(Thread.currentThread(), "delete");
                try {
                    root.delete(this, key, storage, transaction, retryDelete);
                } finally {
                    activeMutatorThreads.remove(Thread.currentThread());
                    activeMutations.decrementAndGet();
                }
            } finally {
                // R-33: см. комментарий в insert
                if (isWriteLocked()) {
                    unlockWrite();
                }
            }
        } while (retryDelete.get());
    }

    public void update(final Comparable key, final Object value, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (Thread.currentThread().isInterrupted()) {
            throw new XdStorageException("interrupted", new InterruptedException("Поток прерван перед обновлением в B+ Дереве"));
        }

        lockWrite(transaction);
        try {
            // Восстановление живого корня для операции обновления структуры
            if (root == null && wasInitialized) {
                XdStorageBTree freshTree = storage.load(XdStorageBTree.class, this.id, transaction);
                if (freshTree != null && freshTree.getRoot() != null) {
                    this.root = freshTree.getRoot();
                    this.firstLeaf = freshTree.getFirstLeaf();
                }
            }

            if (root == null) {
                throw new XdStorageException("object of " + id + " with idgeneration " + key + " does not exists");
            }

            // R-53: парсерный стаб корня (холодное открытие) заменяется каноническим инстансом;
            // firstLeaf — тоже (цепочка листьев: валидатор, сканы)
            root = this.root = XdStorageBTreeNode.materialize(root, storage, transaction, this, null);
            if (firstLeaf != null) {
                firstLeaf = this.firstLeaf = XdStorageBTreeNode.materialize(firstLeaf, storage, transaction, this, null);
            }
        } finally {
            unlockWrite();
        }

        final AtomicLong counter = new AtomicLong(0);
        final AtomicBoolean retryUpdate = new AtomicBoolean();
        int attempts = 0;
        do {
            synchronized (counter) {
                if (counter.incrementAndGet() % 5 == 0) {
                    try {
                        counter.wait(50);
                    } catch (InterruptedException e) {
                        throw new XdStorageException("interrupted", e);
                    }
                }
            }
            if (++attempts > REBALANCE_RETRY_BUDGET) {
                log.warn("B+Tree rebalance retry budget exhausted for update of key {} in tree {} — sustained rebalance lock contention (operation failed loudly)", key,
                        id);
                throw new XdStorageException("retry budget exhausted by update of idgeneration " + key
                        + " in " + id + ": устойчивая конкуренция за блокировки ребалансировки");
            }

            retryUpdate.set(false);
            lockWrite(transaction);
            try {
                if (root == null && wasInitialized) {
                    XdStorageBTree freshTree = storage.load(XdStorageBTree.class, this.id, transaction);
                    if (freshTree != null && freshTree.getRoot() != null) {
                        this.root = freshTree.getRoot();
                        this.firstLeaf = freshTree.getFirstLeaf();
                    }
                }

                if (root == null) {
                    if (XdStorageBTreeNode.CHAIN_DEBUG) {
                        System.out.println("[CHAIN] UPDATE-MISS tree=" + id + " key=" + key + " wasInitialized=" + wasInitialized);
                        new Exception("update-miss-stack").printStackTrace(System.out);
                    }
                    throw new XdStorageException("object of " + id + " with idgeneration " + key + " does not exists");
                }
                structureVersion.incrementAndGet();
                activeMutations.incrementAndGet();
                activeMutatorThreads.put(Thread.currentThread(), "update");
                try {
                    root.update(this, key, value, storage, transaction, retryUpdate);
                } finally {
                    activeMutatorThreads.remove(Thread.currentThread());
                    activeMutations.decrementAndGet();
                }
            } finally {
                // R-33: см. комментарий в insert
                if (isWriteLocked()) {
                    unlockWrite();
                }
            }
        } while (retryUpdate.get());
    }

    public List<Object> find(final Comparable key, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (root == null && wasInitialized) {
            lockWrite(transaction);
            try {
                if (root == null) {
                    XdStorageBTree freshTree = storage.load(XdStorageBTree.class, this.id, transaction);
                    if (freshTree != null && freshTree.getRoot() != null) {
                        this.root = freshTree.getRoot();
                        this.firstLeaf = freshTree.getFirstLeaf();
                    }
                }
            } finally {
                unlockWrite();
            }
        }

        // =========================================================================
        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ НА ДЕБАГЕ (Монолитная синхронизация B+Tree):
        // Если индекс поддерживает дубликаты (isMultiple == true), мы КАТЕГОРИЧЕСКИ запрещаем
        // осуществлять вертикальный спуск по не-листовым этажам, так как сплиты разрывают дубликаты
        // между левой и правой ветками! Мы запускаем канонический сквозной горизонтальный обход
        // листьев по указателям nextTreeNodeOnThisLevel, начиная с firstLeaf!
        // Это гарантирует 100% сбор всех элементов без нарушения знаков < 0 в навигаторах мутаций!
        // =========================================================================
        if (this.multiple) {
            final List<Object> multiResult = new ArrayList<>();
            this.read(storage, transaction, new IXdStorageBTreeViewer() {
                @Override
                public void look(final Comparable objectId, final Object value) {
                    if (objectId != null && objectId.compareTo(key) == 0) {
                        multiResult.add(value);
                    }
                }
            });
            return multiResult;
        }

        if (root != null && XdStorageObjectUtils.isReference(root)) {
            // R-38: СТАБ-УЗЕЛ корня грузится ТОЛЬКО под write-локом самого узла.
            // Раньше дисковый IO выполнялся под write-локом ВСЕГО ДЕРЕВА — все читатели и
            // писатели дерева простаивали весь XML-парс, и на холодном кэше первый поиск
            // вымораживал дерево на секунды (таймауты "монопольно занят на запись" у всех воркеров).
            // R-53: стаб корня заменяется каноническим инстансом по id (ФАЗА 3B идентичность) —
            // без этого спуск шел по пустому узлу и все поиски после реопена возвращали пусто.
            final XdStorageBTreeNode stub = root;
            stub.lockWrite(transaction);
            try {
                if (XdStorageObjectUtils.isReference(stub)) {
                    root = this.root = XdStorageBTreeNode.materialize(stub, storage, transaction, this, null);
                }
            } finally {
                stub.unlockWrite();
            }
        }

        List<Object> result;
        final AtomicBoolean retryFind = new AtomicBoolean();
        // R-54: бюджет поисковых ретраев — ПО ВРЕМЕНИ, не по числу попыток. Попытечный
        // бюджет (R-36, 200 попыток) выгорал за десятки миллисекунд под лавиной из 25
        // писателей: каждый retry заново захватывает read-лок дерева (ожидая чужой мутации),
        // спускается и проигрывает гонку следующему deep-lock — читатель-ЧЕСТНЕЦ погибал
        // громким отказом под легитимной нагрузкой. Теперь ретраи ограничены дедлайном
        // (производная от таймаута транзакции) с экспоненциальным backoff: аллокационный
        // шторм R-36 невозможен (паузы ограничивают темп), а окно ожидания перекрывает
        // штормы; вечный клин отсечен дедлайном + штатным таймаутом lockRead.
        final long txTimeoutMs = transaction != null ? transaction.getTimeout() : 5000L;
        final long findBudgetMs = Math.min(Math.max(txTimeoutMs * 3 / 4, 500L), FIND_RETRY_BUDGET_CAP_MS);
        final long findDeadlineNanos = System.nanoTime() + findBudgetMs * 1_000_000L;
        long backoffMillis = 1L;
        do {
            retryFind.set(false);
            lockRead(transaction);
            // ВНИМАНИЕ (R-35): read-лок релизится самим node-спуском (lock-coupling —
            // XdStorageBTreeNode.find вызывает tree.unlockRead на точке сцепления).
            // Внешний try/finally здесь дал бы ДВОЙНОЙ unlockRead. Известная дыра:
            // если спуски бросают ДО точки сцепления — read-лок течет (R-35 open).
            if (root == null) {
                unlockRead();
                return Collections.emptyList();
            }
            result = root.find(this, key, storage, transaction, retryFind);

            // R-36/R-54: громкий отказ — ТОЛЬКО когда спуск снова не пробился (retryFind)
            // И дедлайн истек. Успешный спуск возвращает результат независимо от числа
            // ретраев (попытечный бюджет умел ронять УСПЕШНУЮ попытку №201).
            if (retryFind.get()) {
                if (System.nanoTime() - findDeadlineNanos >= 0) {
                    io.github.xdstorage.metrics.XdStorageMetrics.engineEvents().increment(io.github.xdstorage.metrics.XdStorageMetrics.FIND_RETRY_BUDGET_EXHAUSTIONS);
                    log.warn("B+Tree find retry budget exhausted after {} ms for key {} in tree {} — sustained write pressure (operation failed loudly)",
                            findBudgetMs, key, id);
                    throw new XdStorageException("find retry budget exhausted by search of idgeneration " + key
                            + " in " + id + ": устойчивая конкуренция за блокировки ребалансировки");
                }

                // R-42/R-54: экспоненциальный backoff — без паузы читатель молотит
                // tryLockRead в цикле, мешая сплитам/джойнам завершиться; растущая пауза
                // дает мутаторам окно и создает safepoint для диагностики.
                try {
                    Thread.sleep(backoffMillis);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new XdStorageException("interrupted during find retry backoff", e);
                }
                backoffMillis = Math.min(backoffMillis * 2, FIND_RETRY_BACKOFF_MAX_MILLIS);
            }
        } while (retryFind.get());
        return result == null ? Collections.emptyList() : result;
    }

    public void read(final IXdStorage storage, final IXdStorageTransaction transaction, final IXdStorageBTreeViewer viewer) throws XdStorageException, XdStorageConnectionException {
        // R-38: ПРЕДПРОГРЕВ ЦЕПИ ЛИСТЬЕВ. На холодном кэше связный обход под read-локами
        // делал синхронный storage.load каждого стаба-узла (XML-парс с диска) ВНУТРИ
        // удерживаемых блокировок — писатели вымораживались на таймаутах (R-37: детерминированный
        // ноль прогрессов в nested-стрессе). Поэтому сначала разрешаем все стабы цепи,
        // держа на время дискового IO только write-лок самого загружаемого узла.
        lockWrite(transaction);
        XdStorageBTreeNode chainStart;
        try {
            chainStart = firstLeaf;
        } finally {
            unlockWrite();
        }
        if (chainStart != null) {
            // R-53: предпрогрев разрешает стабы цепи (включая первый лист) в канонические
            // инстансы и возвращает разрешенное начало цепи для обхода
            chainStart = prewarmLeafChain(chainStart, storage, transaction);
        }

        lockRead(transaction);
        if (chainStart == null) {
            unlockRead();
            return;
        }
        chainStart.read(this, storage, transaction, viewer);
    }

    /**
     * FP-1a (docs/tasks/2.1.0/09): ORDERED BOUNDED walk of the leaf chain — the range-operator
     * fast path. Reuses read()'s prewarm/lock machinery (R-38/R-53) and feeds the viewer only
     * the entries inside [fromKey, toKey]; the walk TERMINATES as soon as the chain passes the
     * upper bound (the chain is ordered, nothing further can re-enter the range). A null bound
     * is open on that side. No vertical descent: for duplicate-bearing trees (search indexes,
     * multiple=true) the engine deliberately forbids descents (splits reorder duplicates across
     * branches — see the invariant comment in find), so positioning starts at the first leaf;
     * early termination is what makes selective upper-bounded ranges sub-linear.
     */
    public void readRange(final Comparable fromKey, final boolean fromInclusive,
                          final Comparable toKey, final boolean toInclusive,
                          final IXdStorage storage, final IXdStorageTransaction transaction,
                          final IXdStorageBTreeViewer viewer) throws XdStorageException, XdStorageConnectionException {
        try {
            read(storage, transaction, new IXdStorageBTreeViewer() {
                @Override
                public void look(final Comparable key, final Object value) throws XdStorageException, XdStorageConnectionException {
                    if (fromKey != null) {
                        final int cmp = key.compareTo(fromKey);
                        if (cmp < 0 || (cmp == 0 && !fromInclusive)) {
                            return;
                        }
                    }
                    if (toKey != null) {
                        final int cmp = key.compareTo(toKey);
                        if (cmp > 0 || (cmp == 0 && !toInclusive)) {
                            throw new RangeWalkAborted();
                        }
                    }
                    viewer.look(key, value);
                }
            });
        } catch (final RangeWalkAborted walkedPastTheRange) {
            // normal completion of a bounded walk — the node walk releases all its locks on the way out
        }
    }

    /** Control-flow signal of readRange: the ordered chain walked past the upper bound. */
    private static final class RangeWalkAborted extends XdStorageRuntimeException {
        private RangeWalkAborted() {
            super(null, null, false, false);
        }
    }

    /**
     * R-38: разрешает (догружает с диска) стабы-узлы вдоль цепи листьев ДО захвата read-локов
     * обхода. Идемпотентно: загруженный канонический инстанс больше не является стабом.
     * НЕБЛОКИРУЮЩЕЕ (R-42): стаб, чей write-лок занят чужим читателем/писателем, ПРОПУСКАЕТСЯ —
     * его догрузит штатный locked-обход. Блокирующий lockWrite здесь скрещивался с coupled
     * читателями встречным порядком и давал взаимные 3-секундные зависания (PhantomRead).
     * R-53: стаб разрешается в канонический инстанс по id; слоты firstLeaf/next чинятся
     * на живой канон. Возвращает разрешенное начало цепи (или null, если startLeaf == null).
     */
    private XdStorageBTreeNode prewarmLeafChain(final XdStorageBTreeNode startLeaf, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (startLeaf == null) {
            return null;
        }
        XdStorageBTreeNode current = startLeaf;
        XdStorageBTreeNode previous = null;
        XdStorageBTreeNode resolvedStart = startLeaf;
        int steps = 0;
        while (current != null && steps++ < PREWARM_STEP_BUDGET) {
            if (XdStorageObjectUtils.isReference(current) && current.tryLockWrite(transaction)) {
                // R-53: лок держим на СТАБЕ (current ниже может переприсвоиться каноном)
                final XdStorageBTreeNode stub = current;
                try {
                    if (XdStorageObjectUtils.isReference(stub)) {
                        final XdStorageBTreeNode materialized =
                                XdStorageBTreeNode.materialize(stub, storage, transaction, this, null);
                        if (materialized != stub) {
                            // чинка слота цепи: первый лист — в поле дерева,
                            // последующие — в next предыдущего (уже канонического) листа
                            if (previous == null) {
                                this.firstLeaf = materialized;
                                resolvedStart = materialized;
                            } else {
                                previous.setNextTreeNodeOnThisLevel(materialized);
                            }
                            current = materialized;
                        }
                    }
                } finally {
                    stub.unlockWrite();
                }
            }
            // Указатель читаем без нодового лока сознательно: гонка со сплитом здесь неопасна —
            // пропущенный стаб догрузит locked-обход; Reference читается атомарно (volatile).
            previous = current;
            current = current.getNextTreeNodeOnThisLevel();
        }
        if (steps >= PREWARM_STEP_BUDGET) {
            log.warn("B+Tree leaf chain prewarm hit the step budget {} in tree {} — possible chain cycle", PREWARM_STEP_BUDGET, id);
        }
        return resolvedStart;
    }
}
