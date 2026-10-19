package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.lock.XdStorageReadWriteLock;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
public class XdStorageBTree implements IXdStorageBTreeNode, org.flib.xdstorage.serialization.IXdStorageSerializationGuard {

    private static final Logger log = LogManager.getLogger(XdStorageBTree.class);

    /** R-36: бюджет ретраев спуска поиска — как REBALANCE_RETRY_BUDGET для мутаций. */
    private static final int MAX_FIND_RETRY_BUDGET = 200;

    /** R-42: пауза на каждом N-м ретрае поиска — окно для мутаций + safepoint. */
    private static final int FIND_RETRY_BACKOFF_INTERVAL = 10;
    private static final long FIND_RETRY_BACKOFF_MILLIS = 1;

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

            if (XdStorageObjectUtils.isReference(root)) {
                root.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(root)) {
                        storage.load(root, transaction);
                    }
                } finally {
                    root.unlockWrite();
                }
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
                root.insert(this, key, object, storage, transaction, retryInsert);
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
        log.info("B+Tree {} created root (first key stored)", id);
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
                if (XdStorageObjectUtils.isReference(root)) {
                    root.lockWrite(transaction);
                    try {
                        if (XdStorageObjectUtils.isReference(root)) {
                            storage.load(root, transaction);
                        }
                    } finally {
                        root.unlockWrite();
                    }
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
                    log.info("B+Tree {} is empty after delete of key {} (root purged)", id, key);
                    return;
                }
                root.delete(this, key, storage, transaction, retryDelete);
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

            if (XdStorageObjectUtils.isReference(root)) {
                root.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(root)) {
                        storage.load(root, transaction);
                    }
                } finally {
                    root.unlockWrite();
                }
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
                root.update(this, key, value, storage, transaction, retryUpdate);
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
            root.lockWrite(transaction);
            try {
                if (XdStorageObjectUtils.isReference(root)) {
                    storage.load(root, transaction);
                }
            } finally {
                root.unlockWrite();
            }
        }

        List<Object> result;
        final AtomicBoolean retryFind = new AtomicBoolean();
        int findAttempts = 0;
        do {
            retryFind.set(true); // Сбрасываем флаг итерации do-while
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

            // R-36: БЮДЖЕТ ПОИСКОВЫХ РЕТРАЕВ. Цикл был НЕОГРАНИЧЕННЫМ: под непрерывным
            // давлением мутаций node-level tryLockRead может не пробиваться бесконечно —
            // воркеры уходили в шторм аллокаций (snapshot-копии на каждом ретрае,
            // ~2ГБ/мин) и висели намертво. Громкий отказ лучше бесконечного клина.
            if (++findAttempts > MAX_FIND_RETRY_BUDGET) {
                org.flib.xdstorage.metrics.XdStorageMetrics.engineEvents().increment(org.flib.xdstorage.metrics.XdStorageMetrics.FIND_RETRY_BUDGET_EXHAUSTIONS);
                log.warn("B+Tree find retry budget exhausted for key {} in tree {} — sustained write pressure (operation failed loudly)", key, id);
                throw new XdStorageException("find retry budget exhausted by search of idgeneration " + key
                        + " in " + id + ": устойчивая конкуренция за блокировки ребалансировки");
            }

            // R-42: спинка на ретрае — без паузы читатель молотит tryLockRead в цикле,
            // мешая сплитам/джойнам завершиться (шторм аллокаций + голодание мутаций);
            // пауза дает мутаторам окно и создает safepoint для диагностики.
            if (findAttempts % FIND_RETRY_BACKOFF_INTERVAL == 0) {
                try {
                    Thread.sleep(FIND_RETRY_BACKOFF_MILLIS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new XdStorageException("interrupted during find retry backoff", e);
                }
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
            prewarmLeafChain(chainStart, storage, transaction);
        }

        lockRead(transaction);
        if (firstLeaf == null) {
            unlockRead();
            return;
        }
        firstLeaf.read(this, storage, transaction, viewer);
    }

    /**
     * R-38: разрешает (догружает с диска) стабы-узлы вдоль цепи листьев ДО захвата read-локов
     * обхода. Идемпотентно: загруженный канонический инстанс больше не является стабом.
     * НЕБЛОКИРУЮЩЕЕ (R-42): стаб, чей write-лок занят чужим читателем/писателем, ПРОПУСКАЕТСЯ —
     * его догрузит штатный locked-обход. Блокирующий lockWrite здесь скрещивался с coupled
     * читателями встречным порядком и давал взаимные 3-секундные зависания (PhantomRead).
     */
    private void prewarmLeafChain(final XdStorageBTreeNode startLeaf, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeNode current = startLeaf;
        int steps = 0;
        while (current != null && steps++ < PREWARM_STEP_BUDGET) {
            if (XdStorageObjectUtils.isReference(current) && current.tryLockWrite(transaction)) {
                try {
                    if (XdStorageObjectUtils.isReference(current)) {
                        storage.load(current, transaction);
                    }
                } finally {
                    current.unlockWrite();
                }
            }
            // Указатель читаем без нодового лока сознательно: гонка со сплитом здесь неопасна —
            // пропущенный стаб догрузит locked-обход; Reference читается атомарно (volatile).
            current = current.getNextTreeNodeOnThisLevel();
        }
        if (steps >= PREWARM_STEP_BUDGET) {
            log.warn("B+Tree leaf chain prewarm hit the step budget {} in tree {} — possible chain cycle", PREWARM_STEP_BUDGET, id);
        }
    }
}
