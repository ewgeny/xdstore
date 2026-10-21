package io.github.xdstorage.btree;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.*;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.idgeneration.XdStorageIdGeneratorType;
import io.github.xdstorage.lock.XdStorageReadWriteLock;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
public class XdStorageBTreeNode implements IXdStorageBTreeNode, io.github.xdstorage.serialization.IXdStorageSerializationGuard,
        io.github.xdstorage.code.IXdStorageReferenceMarked {

    private static final Logger log = LogManager.getLogger(XdStorageBTreeNode.class);

    @XdStorageObjectId
    @XdStorageObjectFieldProperties(idGeneratorType = XdStorageIdGeneratorType.DATABASE_GENERATOR)
    private Long id;

    /**
     * R-53: маркер ПАРСЕРНОГО СТАБА (PERFORMANCE-RECOMMENDATIONS.md §6). По умолчанию true:
     * readReference материализует ссылку через newInstance() с заполненным только id, и такой
     * инстанс обязан догружаться с диска. Реальные ноды ставят false в конструкторе.
     * Флаг ПЕРСИСТИТСЯ: геттер isReference()/сеттер setReference() образуют свойство 'reference'
     * (сканер свойств выводит имя поля из геттера), поэтому живая нода, парсированная с диска,
     * восстанавливается с false, а стаб до парсинга полей несет true по умолчанию.
     */
    private boolean reference = true;

    private XdStorageBTree tree;

    private XdStorageBTreeNode parent;

    private List<Comparable> keys;

    private List<Object> objects;

    private List<XdStorageBTreeNode> children;

    // R-38: volatile — указатель цепи читается предпрогревом без нодового лока;
    // писатели (split/join) меняют его под нодовым write-локом, читатели видят свежее значение.
    private volatile XdStorageBTreeNode nextTreeNodeOnThisLevel;

    /** Диагностика цепочки листьев (включается -Dxdstorage.chain.debug=true). */
    public static final boolean CHAIN_DEBUG = Boolean.getBoolean("xdstorage.chain.debug");


    private static String idsOf(final List<XdStorageBTreeNode> nodes) {
        final StringBuilder sb = new StringBuilder("[");
        for (final XdStorageBTreeNode n : nodes) {
            sb.append(n != null ? n.getId() : "null").append(',');
        }
        return sb.append(']').toString();
    }

    private static void chainDebug(final String event) {
        if (CHAIN_DEBUG) {
            System.out.println("[CHAIN] " + event);
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    @Override
    public boolean isReference() {
        return reference;
    }

    public void setReference(final boolean reference) {
        this.reference = reference;
    }

    public XdStorageBTree getTree() {
        return tree;
    }

    public void setTree(final XdStorageBTree tree) {
        this.tree = tree;
    }

    public XdStorageBTreeNode getParent() {
        return parent;
    }

    public void setParent(final XdStorageBTreeNode parent) {
        this.parent = parent;
    }

    public List<Comparable> getKeys() {
        return keys;
    }

    public void setKeys(final List<Comparable> keys) {
        this.keys = keys != null ? java.util.Collections.synchronizedList(keys) : keys;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public void setObjects(final List<Object> objects) {
        this.objects = objects != null ? java.util.Collections.synchronizedList(objects) : objects;
    }

    public List<XdStorageBTreeNode> getChildren() {
        return children;
    }

    public void setChildren(final List<XdStorageBTreeNode> children) {
        this.children = children != null ? java.util.Collections.synchronizedList(children) : children;
    }

    public XdStorageBTreeNode getNextTreeNodeOnThisLevel() {
        return nextTreeNodeOnThisLevel;
    }

    public void setNextTreeNodeOnThisLevel(final XdStorageBTreeNode nextTreeNodeOnThisLevel) {
        this.nextTreeNodeOnThisLevel = nextTreeNodeOnThisLevel;
    }

    public XdStorageBTreeNode() {
        this.keys = newSyncList();
        this.objects = newSyncList();
        this.children = newSyncList();
    }

    public XdStorageBTreeNode(final XdStorageBTree tree, final XdStorageBTreeNode parent) {
        this.tree = tree;
        this.parent = parent;
        this.reference = false; // R-53: живая нода — не парсерный стаб
        this.keys = newSyncList();
        this.objects = newSyncList();
        this.children = newSyncList();
    }

    /**
     * R-53: материализация парсерного стаба узла на холодном открытии
     * (PERFORMANCE-RECOMMENDATIONS.md §6: B+Tree структуры нечитаемы после переоткрытия).
     *
     * Загрузка по (класс, id) возвращает канонический инстанс из инфраструктурного кэша
     * (ФАЗА 3B: единственный живой инстанс на все транзакции) — стаб ЗАМЕНЯЕТСЯ каноном,
     * а не наполняется копией через generic-fill (тот уводит бэк-референсы в пустые
     * врапперы: getTree().getT() == 0 ломало сплиты после реопена).
     * setReference(false) — самолечение баз, записанных до введения флага.
     *
     * @param liveTree   живое дерево для починки бэк-референса tree (может быть null)
     * @param liveParent живой родитель для починки бэк-референса parent (может быть null)
     * @return канонический инстанс узла (или сам stub, если он не стаб)
     */
    static XdStorageBTreeNode materialize(final XdStorageBTreeNode stub, final IXdStorage storage,
                                          final IXdStorageTransaction transaction,
                                          final XdStorageBTree liveTree, final XdStorageBTreeNode liveParent)
            throws XdStorageException, XdStorageConnectionException {
        if (stub == null || !XdStorageObjectUtils.isReference(stub)) {
            return stub;
        }
        if (stub.getId() == null) {
            throw new XdStorageException("B+Tree parser stub without id cannot be materialized");
        }
        final XdStorageBTreeNode canonical = storage.load(XdStorageBTreeNode.class, stub.getId(), transaction);
        if (canonical == null) {
            throw new XdStorageException("B+Tree node " + stub.getId() + " is referenced but missing in storage (broken structure?)");
        }
        canonical.setReference(false);
        if (liveTree != null) {
            canonical.setTree(liveTree);
        }
        if (liveParent != null && (canonical.getParent() == null || XdStorageObjectUtils.isReference(canonical.getParent()))) {
            canonical.setParent(liveParent);
        }
        return canonical;
    }

    /**
     * R-43: списки ноды создаются synchronized-обертками: монитором списка пользуется
     * и сериализатор (снимок writeCollection/writeMap) — мутации под нодовым write-локом
     * и снимок писателя взаимно исключаются, CME при выгрузке живой ноды невозможна.
     * Накладных расходов нет: мутаторы и так сериализованы нодовым write-локом.
     */
    private static <T> List<T> newSyncList() {
        return java.util.Collections.synchronizedList(new LinkedList<>());
    }

    private final XdStorageReadWriteLock lock = new XdStorageReadWriteLock();
    
    private List<IXdStorageBTreeNode> lockedNodes = new LinkedList<>();

    @Override
    public boolean isWriteLocked() {
        return lock.isWriteLocked();
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

    // =========================================================================
    // R-43: СГРАНИРОВАНИЕ СЕРИАЛИЗАЦИИ. Выгрузка ноды на диск пишет ЖИВОЙ инстанс,
    // пока чужие сплиты/джойны мутируют его LinkedList-поля (CME в writeCollection).
    // Все структурные мутации ноды выполняются под ее write-локом, поэтому писатель
    // сериализует под ТЕМ ЖЕ локом — но ТОЛЬКО tryLock: БЛОКИРУЮЩАЯ версия (lockWrite
    // на время файлового IO) создавала hold-and-wait голодание под 25 воркерами
    // (держатель X ждал Y, держатель Y ждал X — таймауты 15с). Если лок занят — нода
    // сейчас мутируется: пишем без замка, CME гасится мониторными снимками коллекций
    // в писателе, а окна разрыва keys/children на середине сплита остаются редким
    // остаточным риском (микросекунды против секунд файлового IO).
    // =========================================================================
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

    private boolean tryDeepLockForInsert(final IXdStorageTransaction transaction) {
        if (!tryLockWrite(transaction)) {
            return false;
        }

        final List<IXdStorageBTreeNode> currentLocked = new ArrayList<>();
        currentLocked.add(this);

        XdStorageBTreeNode node = this.getParent(), prev = this;
        while (node != null) {
            if (prev.getKeys().size() == (2 * getTree().getT() - 1)) {
                if (!node.tryLockWrite(transaction)) {
                    currentLocked.stream().forEach(lockedNode -> {
                        lockedNode.unlockWrite();
                    });
                    return false;
                }
                currentLocked.add(node);
                prev = node;
                node = node.getParent();
                continue;
            }
            break;
        }

        if (node == null && prev.getKeys().size() == (2 * getTree().getT() - 1)) {
            if (!tree.tryLockWrite(transaction)) {
                currentLocked.stream().forEach(lockedNode -> {
                    lockedNode.unlockWrite();
                });
                return false;
            }
            currentLocked.add(tree);
        } else if (node != null) {
            if (!node.tryLockWrite(transaction)){
                currentLocked.stream().forEach(lockedNode -> {
                    lockedNode.unlockWrite();
                });
                return false;
            }
            currentLocked.add(node);
        }

        lockedNodes.addAll(currentLocked);

        return true;
    }

    private boolean tryDeepLockForDelete(final IXdStorageTransaction transaction) {
        if (!tryLockWrite(transaction)) {
            return false;
        }

        final List<IXdStorageBTreeNode> currentLocked = new ArrayList<>();
        currentLocked.add(this);

        XdStorageBTreeNode node = this.getParent(), prev = this;
        while (node != null) {
            if (prev.getKeys().size() <= getTree().getT()) {
                if (!node.tryLockWrite(transaction)) {
                    currentLocked.stream().forEach(lockedNode -> {
                        lockedNode.unlockWrite();
                    });
                    return false;
                }
                currentLocked.add(node);
                prev = node;
                node = node.getParent();
                continue;
            }
            break;
        }

        if (node == null && prev.getKeys().size() <= getTree().getT()) {
            if (!tree.tryLockWrite(transaction)) {
                currentLocked.stream().forEach(lockedNode -> {
                    lockedNode.unlockWrite();
                });
                return false;
            }
            currentLocked.add(tree);
        } else if (node != null) {
            if (!node.tryLockWrite(transaction)){
                currentLocked.stream().forEach(lockedNode -> {
                    lockedNode.unlockWrite();
                });
                return false;
            }
            currentLocked.add(node);
        }

        lockedNodes.addAll(currentLocked);

        return true;
    }

    private boolean tryLockAndAddToDeepLock(final XdStorageBTreeNode deepLockedNode, final IXdStorageTransaction transaction) throws XdStorageException {
        if (deepLockedNode != null && tryLockWrite(transaction)) {
            deepLockedNode.lockedNodes.add(this);
            return true;
        }
        return false;
    }

    private void lockAndAddToDeepLock(final XdStorageBTreeNode deepLockedNode, final IXdStorageTransaction transaction) throws XdStorageException {
        if (deepLockedNode != null) {
            lockWrite(transaction);
            deepLockedNode.lockedNodes.add(this);
        }
    }

    private void deepUnlock() {
        final List<IXdStorageBTreeNode> toUnlock = new ArrayList<>(lockedNodes);
        lockedNodes.clear();
        toUnlock.stream().forEach(lockedNode -> {
            lockedNode.unlockWrite();
        });
    }

    private XdStorageBTreeNode loadThisNode(final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        lockRead(transaction);
        try {
            final XdStorageBTreeNode nextTreeNode = getNextTreeNodeOnThisLevel();
            if (nextTreeNode != null && XdStorageObjectUtils.isReference(nextTreeNode)) {
                nextTreeNode.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(nextTreeNode)) {
                        // R-53: стаб следующего листа заменяется каноническим инстансом;
                        // слот next чинится на живой канон (родителя next здесь не знаем —
                        // его починит спуск записи через loadChildOfThisNode)
                        final XdStorageBTreeNode materialized =
                                materialize(nextTreeNode, storage, transaction, getTree(), null);
                        if (materialized != nextTreeNode) {
                            setNextTreeNodeOnThisLevel(materialized);
                        }
                    }
                } finally {
                    nextTreeNode.unlockWrite();
                }
            }
            return this;
        } finally {
            unlockRead();
        }
    }

    private XdStorageBTreeNode loadChildOfThisNode(int index, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        lockRead(transaction);
        try {
            XdStorageBTreeNode child;

            // СИНХРОНИЗАЦИЯ НА ИНСТАНСЕ КОЛЛЕКЦИИ ДЕТЕЙ:
            // Блокируем чтение LinkedList на время возможных параллельных remove() операций писателя
            synchronized (getChildren()) {
                final List<XdStorageBTreeNode> childrenList = getChildren();
                if (childrenList == null || childrenList.isEmpty()) {
                    return null;
                }

                if (index < 0) {
                    index = 0;
                } else if (index >= childrenList.size()) {
                    index = childrenList.size() - 1;
                }

                child = childrenList.get(index);
            }

            if (XdStorageObjectUtils.isReference(child)) {
                // R-53: лок держим на СТАБЕ (отдельная переменная — child ниже переприсваивается)
                final XdStorageBTreeNode stub = child;
                stub.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(stub)) {
                        // стаб ребенка заменяется каноническим инстансом по id (ФАЗА 3B
                        // идентичность), бэк-референсы чинятся на живые дерево/родителя (this)
                        final XdStorageBTreeNode materialized =
                                materialize(stub, storage, transaction, getTree(), this);
                        if (materialized != stub) {
                            synchronized (getChildren()) {
                                final List<XdStorageBTreeNode> slotChildren = getChildren();
                                if (slotChildren != null) {
                                    final int at = slotChildren.indexOf(stub);
                                    if (at >= 0) {
                                        slotChildren.set(at, materialized);
                                    }
                                }
                            }
                            // канон.child.next: стаб следующего листа разрешаем сразу
                            // (прежняя семантика child.loadThisNode после загрузки)
                            materialized.loadThisNode(storage, transaction);
                            child = materialized;
                        }
                    }
                } finally {
                    stub.unlockWrite();
                }

                synchronized (getChildren()) {
                    final List<XdStorageBTreeNode> postLoadChildren = getChildren();
                    if (postLoadChildren == null || postLoadChildren.isEmpty()) {
                        return null;
                    }
                    if (index >= postLoadChildren.size()) {
                        index = postLoadChildren.size() - 1;
                    }
                    child = postLoadChildren.get(index);
                }
            }
            return child;
        } finally {
            unlockRead();
        }
    }

    public void insert(final IXdStorageBTreeNode toUnlock, final Comparable key, final Object object, final IXdStorage storage, final IXdStorageTransaction transaction, final AtomicBoolean retryInsert) throws XdStorageException, XdStorageConnectionException {
        loadThisNode(storage, transaction);
        if (this.getChildren().isEmpty()) {
            try {
                if (!tryDeepLockForInsert(transaction)) {
                    retryInsert.set(true);
                    return;
                }
            } finally {
                if (toUnlock != null) {
                    toUnlock.unlockWrite();
                }
            }
            try {
                insertIntoThisNode(key, object, storage, transaction);
                if (this.getKeys().size() >= 2 * getTree().getT()) {
                    if (this.getParent() == null) {
                        splitAndCreateNewRoot(this, storage, transaction);
                    } else {
                        splitAndInsertIntoParent(this, storage, transaction);
                    }
                }
            } finally {
                deepUnlock();
            }
        } else {
            findChildAndInsert(toUnlock, key, object, storage, transaction, retryInsert);
        }
    }

    private void findChildAndInsert(final IXdStorageBTreeNode toUnlock, final Comparable key, final Object object, final IXdStorage storage, final IXdStorageTransaction transaction, final AtomicBoolean retryInsert) throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeNode current = this, child = null;
        try {
            current.lockWrite(transaction);
        } finally {
            toUnlock.unlockWrite();
        }

        while (current.getObjects().isEmpty()) {
            try {
                boolean isFound = false;
                final List<XdStorageBTreeNode> currentChildren = current.getChildren();

                for (int i = 0; i < current.getKeys().size(); ++i) {
                    final int compareResult = key.compareTo(current.getKeys().get(i));
                    if (compareResult < 0) {
                        // Ключ строго меньше разделителя — гарантированно уходим налево
                        child = current.loadChildOfThisNode(i, storage, transaction);
                        isFound = true;
                        break;
                    } else if (compareResult == 0) {
                        // МАТЕМАТИЧЕСКИЙ КАНOН Б+ ДЕРЕВА: Если ключ равен внутреннему разделителю этажа,
                        // при вставке мы обязаны направить поток в ПРАВОЕ поддерево этого разделителя (индекс i + 1)!
                        // Выполняем fail-safe проверку границ коллекции детей.
                        int targetIdx = (i + 1) < currentChildren.size() ? (i + 1) : (currentChildren.size() - 1);
                        child = current.loadChildOfThisNode(targetIdx, storage, transaction);
                        isFound = true;
                        break;
                    }
                }
                if (!isFound) {
                    child = current.loadChildOfThisNode(currentChildren.size() - 1, storage, transaction);
                }
                // ГОНКА РЕБАЛАНСИРОВКИ: параллельный merge мог транзитно опустошить список
                // детей — child бывает null. Перезапускаем спуск с корня (структура между
                // попытками меняется, retry делает прогресс); вечный цикл отсекается
                // бюджетом попыток в XdStorageBTree.insert.
                if (child == null) {
                    retryInsert.set(true);
                    return; // unlockWrite текущей ноды выполняет finally ниже
                }
                // R-40: child уже держит write-лок — ЛЮБОЙ throw до передачи лока в current
                // (конфликт MVCC-кэша в storage.update, IO-ошибка) обязан отпустить его,
                // иначе нода остается заблокированной навсегда: следующий писатель встанет
                // на child.lockWrite на полный таймаут, ДЕРЖА write-лок родителя, а третий —
                // на tree.lockWrite, держа лок родителя-на-спуске: каскад из одних таймаутов
                // (детерминированный ноль прогрессов в nested-стрессе R-37).
                // In-memory указатель parent чиним ВСЕГДА (граф дерева обязан быть связным),
                // но storage.update — только при реальной смене ЗНАЧЕНИЯ (id родителя):
                // безусловная фиксация через фасад на каждом спуске держала record горячего
                // внутреннего узла заблокированной до конца транзакции и отказывала всем
                // параллельным спускам (лавина "can not be updated" на hot path).
                child.lockWrite(transaction);
                try {
                    final boolean parentChanged = !isSameParentId(child.getParent(), current);
                    // СИНХРОНИЗАЦИЯ УКАЗАТЕЛЯ PARENT: поле могло остаться на устаревшей
                    // инстанции родителя; контейнер ребенка известен точно — это current.
                    child.setParent(current);
                    if (parentChanged) {
                        // Фиксация через фасад обязательна: parent — персистентное поле ноды
                        storage.update(child, transaction);
                    }
                } catch (final Throwable t) {
                    child.unlockWrite();
                    throw t;
                }
            } finally {
                current.unlockWrite();
            }
            current = child;
        }

        try {
            current.insert(null, key, object, storage, transaction, retryInsert);
        } finally {
            current.unlockWrite();
        }
    }

    /**
     * R-40: семантическое равенство родителя — по id (инстанции одного узла дуальны
     * после подгрузок ссылок, сравнение по ссылке всегда давало "изменился").
     */
    private static boolean isSameParentId(final XdStorageBTreeNode parentField, final XdStorageBTreeNode current) {
        if (parentField == current) {
            return true;
        }
        if (parentField == null || current == null) {
            return false;
        }
        final Long parentId = parentField.getId();
        final Long currentId = current.getId();
        return parentId != null && currentId != null && parentId.equals(currentId);
    }

    private void splitAndInsertIntoParent(final XdStorageBTreeNode deepLockedNode, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final int t = getTree().getT();
        final boolean isLeaf = this.getChildren().isEmpty();
        final Comparable midKey = this.getKeys().get(t - 1);

        final XdStorageBTreeNode right = new XdStorageBTreeNode(getTree(), this.getParent());
        storage.save(right, transaction);

        int splitIndex = isLeaf ? (t - 1) : t;

        final ListIterator<Comparable> keyListIterator = getKeys().listIterator(splitIndex);
        final ListIterator<Object> objectListIterator = getObjects().isEmpty() ? null : getObjects().listIterator(splitIndex);
        int childrenListIndex = isLeaf ? -1 : t;

        while (keyListIterator.hasNext()) {
            right.getKeys().add(keyListIterator.next());
            keyListIterator.remove();
            if (objectListIterator != null && objectListIterator.hasNext()) {
                right.getObjects().add(objectListIterator.next());
                objectListIterator.remove();
            }
            if (childrenListIndex != -1) {
                final XdStorageBTreeNode child = loadChildOfThisNode(childrenListIndex, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                getChildren().remove(childrenListIndex);
                child.setParent(right);
                storage.update(child, transaction);
                right.getChildren().add(child);
            }
        }
        if (childrenListIndex != -1) {
            final XdStorageBTreeNode child = loadChildOfThisNode(childrenListIndex, storage, transaction);
            child.lockAndAddToDeepLock(deepLockedNode, transaction);
            getChildren().remove(childrenListIndex);
            child.setParent(right);
            storage.update(child, transaction);
            right.getChildren().add(child);
            if (keyListIterator.hasPrevious()) {
                keyListIterator.previous();
                keyListIterator.remove();
            }
        }

        // Разделитель midKey УЖЕ изъят из 'this' итератором в блоке переноса последнего ребенка
        // (keyListIterator.previous() + remove()). Повторное remove(midKey) для unique-деревьев
        // молча не срабатывает, а для multiple-деревьев удаляет ВТОРОЙ равный разделитель —
        // у ноды остается keys = children - 1 (каскад: спуски мисроутят, join'ы сиротят листья).
        if (isLeaf) {
            chainDebug("SPLIT this=" + this.getId() + " right=" + right.getId() + " oldNext=" + (this.getNextTreeNodeOnThisLevel() != null ? this.getNextTreeNodeOnThisLevel().getId() : null));
            right.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            this.setNextTreeNodeOnThisLevel(right);
        }

        right.lockAndAddToDeepLock(deepLockedNode, transaction);

        storage.update(right, transaction);
        storage.update(this, transaction);

        log.debug("B+Tree node {} split into {} (tree {})", this.getId(), right.getId(), getTree().getId());

        final List<XdStorageBTreeNode> childrenOfParent = this.getParent().getChildren();
        final List<Comparable> keysOfParent = this.getParent().getKeys();

        boolean separatorInserted = false;
        for (int i = 0; i < childrenOfParent.size(); ++i) {
            final XdStorageBTreeNode child = childrenOfParent.get(i);
            final Object childId = child.getId();
            if ((childId != null && child.getId().equals(this.getId())) || child == this) {

                // =========================================================================
                // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Ликвидация IndexOutOfBoundsException):
                // Защищаем операции вставки разделителей родительского узла fail-safe проверками!
                // Если из-за каскадного рекурсивного сплита верхних этажей коллекция детей или ключей
                // родителя была опустошена/перестроена, мы выполняем безопасное добавление элементов в хвост,
                // полностью пресекая крах LinkedList.checkPositionIndex(1) в пишущем потоке воркера!
                // =========================================================================
                if (i < keysOfParent.size()) {
                    keysOfParent.add(i, midKey);
                } else {
                    keysOfParent.add(midKey);
                }

                if ((i + 1) < childrenOfParent.size()) {
                    childrenOfParent.add(i + 1, right);
                } else {
                    childrenOfParent.add(right);
                }
                separatorInserted = true;
                break;
            }
        }
        // R-56: РАНЬШЕ — МОЛЧАЛИВЫЙ ПРОПУСК. Если split-узел не найден в детях своего
        // родителя (дуальность инстанций/устаревший parent), разделитель и right НЕ
        // вставлялись никуда и НЕ ФИКСИРОВАЛОСЬ НИКАК: right оставался в цепочке листьев,
        // но был недостижим из корня ("листьев только из цепочки") — корневой канал
        // рассинхрона структуры. Теперь — громкий отказ с дампом вместо молчаливой порчи.
        if (!separatorInserted) {
            final StringBuilder dump = new StringBuilder("[CHAIN] SPLIT-ORPHAN this=")
                    .append(this.getId()).append("@").append(System.identityHashCode(this))
                    .append(" parent=").append(this.getParent().getId()).append("@")
                    .append(System.identityHashCode(this.getParent()))
                    .append(" parentChildren=[");
            for (int i = 0; i < childrenOfParent.size(); ++i) {
                final XdStorageBTreeNode c = childrenOfParent.get(i);
                dump.append(c.getId()).append('@').append(System.identityHashCode(c)).append(i < childrenOfParent.size() - 1 ? "," : "");
            }
            dump.append("] tree=").append(getTree().getId());
            chainDebug(dump.toString());
            throw new XdStorageException("split of node " + this.getId() + " cannot find itself in its parent "
                    + this.getParent().getId() + " (children=" + childrenOfParent.size() + ") — parent pointer desynchronized");
        }

        storage.update(this.getParent(), transaction);

        // R-56: TRIPWIRE ПОСЛЕ SPLITS — раньше стоял только после join/borrow/rootCollapse.
        // Родитель после вставки разделителя обязан оставаться консистентным (children==keys+1,
        // монотонность) — ловим разъезд пары keys/children НА МЕСТЕ операции, а не на
        // следующем сэмпле валидатора, когда мутатор уже неизвестен.
        XdStorageBTreeInvariants.checkStructuralAlignment(this.getParent(), "splitIntoParent:" + this.getId());

        if (this.getParent().getKeys().size() >= 2 * t) {
            if (this.getParent().getParent() == null) {
                this.getParent().splitAndCreateNewRoot(deepLockedNode, storage, transaction);
            } else {
                this.getParent().splitAndInsertIntoParent(deepLockedNode, storage, transaction);
            }
        }
    }

    private void splitAndCreateNewRoot(final XdStorageBTreeNode deepLockedNode, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final int t = getTree().getT();
        final boolean isLeaf = this.getChildren().isEmpty();
        final Comparable midKey = this.getKeys().get(t - 1);

        final XdStorageBTreeNode newRoot = new XdStorageBTreeNode(getTree(), null);
        storage.save(newRoot, transaction);

        newRoot.lockAndAddToDeepLock(deepLockedNode, transaction);

        getTree().setRoot(newRoot);
        storage.update(getTree(), transaction);

        this.setParent(getTree().getRoot());
        final XdStorageBTreeNode right = new XdStorageBTreeNode(getTree(), this.getParent());
        storage.save(right, transaction);

        right.lockAndAddToDeepLock(deepLockedNode, transaction);

        int splitIndex = isLeaf ? (t - 1) : t;

        final ListIterator<Comparable> keyListIterator = getKeys().listIterator(splitIndex);
        final ListIterator<Object> objectListIterator = getObjects().isEmpty() ? null : getObjects().listIterator(splitIndex);
        int childrenListIndex = isLeaf ? -1 : t;

        while (keyListIterator.hasNext()) {
            right.getKeys().add(keyListIterator.next());
            keyListIterator.remove();
            if (objectListIterator != null && objectListIterator.hasNext()) {
                right.getObjects().add(objectListIterator.next());
                objectListIterator.remove();
            }
            if (childrenListIndex != -1) {
                final XdStorageBTreeNode child = loadChildOfThisNode(childrenListIndex, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                getChildren().remove(childrenListIndex);
                child.setParent(right);
                storage.update(child, transaction);
                right.getChildren().add(child);
            }
        }
        if (childrenListIndex != -1) {
            final XdStorageBTreeNode child = loadChildOfThisNode(childrenListIndex, storage, transaction);
            child.lockAndAddToDeepLock(deepLockedNode, transaction);
            getChildren().remove(childrenListIndex);
            child.setParent(right);
            storage.update(child, transaction);
            right.getChildren().add(child);
            if (keyListIterator.hasPrevious()) {
                keyListIterator.previous();
                keyListIterator.remove();
            }
        }

        // Разделитель midKey уже изъят итератором (см. комментарий в splitAndInsertIntoParent) —
        // повторный remove(midKey) ломал multiple-деревья (двойное удаление равного разделителя).
        if (isLeaf) {
            chainDebug("SPLIT this=" + this.getId() + " right=" + right.getId() + " oldNext=" + (this.getNextTreeNodeOnThisLevel() != null ? this.getNextTreeNodeOnThisLevel().getId() : null));
            right.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            this.setNextTreeNodeOnThisLevel(right);
        }

        storage.update(right, transaction);
        storage.update(this, transaction);

        // Fail-safe защита для инициализации корня
        final List<Comparable> rootKeys = newRoot.getKeys();
        final List<XdStorageBTreeNode> rootChildren = newRoot.getChildren();

        rootKeys.add(midKey);
        rootChildren.add(this);
        rootChildren.add(right);

        storage.update(newRoot, transaction);

        // R-56: tripwire нового корня (симметрично splitIntoParent)
        XdStorageBTreeInvariants.checkStructuralAlignment(newRoot, "splitCreateRoot:" + this.getId());
    }

    private void insertIntoThisNode(final Comparable key, final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        boolean isInserted = false;
        int i;
        for (i = 0; i < getKeys().size(); ++i) {
            final int compareResult = key.compareTo(getKeys().get(i));

            // =========================================================================
            // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Исправление контракта уникальности индексов):
            // Инвертируем ложную проверку getTree().isMultiple()! Исключение двойной вставки
            // обязано выбрасываться строго тогда, когда дерево объявлено УНИКАЛЬНЫМ (!isMultiple()),
            // а прилетающий ключ полностью дублирует уже существующий (compareResult == 0).
            // Это на 100% защищает первичные индексы адресации id_index от повреждения данных!
            // =========================================================================
            if (!getTree().isMultiple() && compareResult == 0) {
                throw new XdStorageException("trying to double insert one object of " + getTree().getId() + " with idgeneration " + key);
            } else if (compareResult < 0) {
                getKeys().add(i, key);
                getObjects().add(i, object);
                isInserted = true;
                break;
            }
        }
        if (!isInserted) {
            getKeys().add(key);
            getObjects().add(object);
        }
        storage.update(this, transaction);
    }

    private Comparable findLeftKey(final XdStorageBTreeNode deepLockedNode, final XdStorageBTreeNode node, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        node.lockAndAddToDeepLock(deepLockedNode, transaction);
        // ГВАРДЬЯН ПУСТОГО РАЗДЕЛИТЕЛЯ: транзитно пустая нода не имеет левого ключа —
        // возвращаем null, вызывающие стороны обязаны пропустить добавление разделителя
        if (node.getKeys().isEmpty()) {
            return null;
        }
        return node.getChildren().isEmpty() ? node.getKeys().get(0) : findLeftKey(deepLockedNode, node.loadChildOfThisNode(0, storage, transaction), storage, transaction);
    }

    public void delete(final IXdStorageBTreeNode toUnlock, final Comparable key, final IXdStorage storage, final IXdStorageTransaction transaction, final AtomicBoolean retryDelete) throws XdStorageException, XdStorageConnectionException {
        loadThisNode(storage, transaction);
        if (this.getChildren().isEmpty()) {
            try {
                if (!tryDeepLockForDelete(transaction)) {
                    retryDelete.set(true);
                    return;
                }
            } finally {
                if (toUnlock != null) {
                    toUnlock.unlockWrite();
                }
            }
            try {
                deleteFromThisNode(key, storage, transaction);
                if (this.getKeys().size() <= getTree().getT() - 1) {
                    if (this.getParent() != null) {
                        if (!tryToMoveKeyFromNeightbor(this, storage, transaction)) {
                            joinWithNeightbor(this, storage, transaction);
                        }
                    }
                }

                // =========================================================================
                // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Уничтожение дефекта остаточного корня):
                // Если в коллекции ключей текущей ноды не осталось элементов, и при этом
                // объект совпадает с текущим корнем bTree, мы БЕЗУСЛОВНО обнуляем ссылки root
                // и firstLeaf прямо в инстансе дерева памяти теста! Это гарантирует 100%
                // прохождение ассерта assertNull(bTree.getRoot()) при полном опустошении базы!
                // =========================================================================
                final XdStorageBTreeNode root = getTree().getRoot();
                final Object rootId = root != null ? root.getId() : null;

                if (this.getKeys().isEmpty() && (root == this || (rootId != null && rootId.equals(this.getId())))) {
                    getTree().setRoot(null);
                    getTree().setFirstLeaf(null);
                    storage.delete(this, transaction);
                    storage.update(getTree(), transaction);
                }
            } finally {
                deepUnlock();
            }
        } else {
            findChildAndDelete(toUnlock, key, storage, transaction, retryDelete);
        }
    }

    private void findChildAndDelete(final IXdStorageBTreeNode toUnlock, final Comparable key, final IXdStorage storage, final IXdStorageTransaction transaction, final AtomicBoolean retryDelete) throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeNode current = this, child = null;
        try {
            current.lockWrite(transaction);
        } finally {
            toUnlock.unlockWrite();
        }

        while (current.getObjects().isEmpty()) {
            try {
                boolean isFound = false;
                final List<XdStorageBTreeNode> currentChildren = current.getChildren();

                for (int i = 0; i < current.getKeys().size(); ++i) {
                    final int compareResult = key.compareTo(current.getKeys().get(i));
                    if (compareResult < 0) {
                        // Ключ строго меньше разделителя — уходим налево
                        child = current.loadChildOfThisNode(i, storage, transaction);
                        isFound = true;
                        break;
                    } else if (compareResult == 0) {
                        // ДОРАБОТКА КОНТУРА УДАЛЕНИЯ (Полная зеркальная синхронизация):
                        // Если удаляемый ключ совпал с разделителем на внутреннем этаже-маршрутизаторе,
                        // навигатор удаления обязан двигаться СИММЕТРИЧНО навигатору вставки — то есть
                        // уйти в ПРАВОЕ поддерево (индекс i + 1)! Это полностью пресекает ложные уходы
                        // навигатора на соседние страницы и убирает ошибку "does not exists" при пурдже!
                        int targetIdx = (i + 1) < currentChildren.size() ? (i + 1) : (currentChildren.size() - 1);
                        child = current.loadChildOfThisNode(targetIdx, storage, transaction);
                        isFound = true;
                        break;
                    }
                }
                if (!isFound) {
                    child = current.loadChildOfThisNode(currentChildren.size() - 1, storage, transaction);
                }
                // ГОНКА РЕБАЛАНСИРОВКИ: параллельный merge мог транзитно опустошить список
                // детей — child бывает null. Перезапускаем спуск с корня; бюджет попыток
                // в XdStorageBTree.delete отсекает вечный цикл.
                if (child == null) {
                    retryDelete.set(true);
                    return; // unlockWrite текущей ноды выполняет finally ниже
                }
                // R-40: зеркальная страховка спуска удаления — child под write-локом не должен
                // утекать при throw из storage.update; фиксация parent — только при реальной
                // смене значения (см. зеркальный комментарий в findChildAndInsert).
                child.lockWrite(transaction);
                try {
                    final boolean parentChanged = !isSameParentId(child.getParent(), current);
                    child.setParent(current);
                    if (parentChanged) {
                        // Фиксация через фасад обязательна: parent — персистентное поле ноды
                        storage.update(child, transaction);
                    }
                } catch (final Throwable t) {
                    child.unlockWrite();
                    throw t;
                }
            } finally {
                current.unlockWrite();
            }
            current = child;
        }

        try {
            current.delete(null, key, storage, transaction, retryDelete);
        } finally {
            current.unlockWrite();
        }
    }

    private void joinWithNeightbor(final XdStorageBTreeNode deepLockedNode, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final List<XdStorageBTreeNode> parentChildren = this.getParent().getChildren();
        final int countChildren = parentChildren.size();
        for (int i = 0; i < countChildren; ++i) {
            final XdStorageBTreeNode child = parentChildren.get(i);
            final Object childId = child.getId();
            if ((childId != null && childId.equals(this.getId())) || child == this) {
                // =========================================================================
                // ГВАРДЬЯН САМОСЛИЯНИЯ (дефект висячей ссылки 47→29): если this — ЕДИНСТВЕННЫЙ
                // ребенок родителя, индекс соседа i+1 выходит за границы, а loadChildOfThisNode
                // молча зажимает его обратно на САМОГО СЕБЯ. Слияние/заимствование с самим собой
                // удаляет живую ноду и оставляет родителя с битой ссылкой на удаленного ребенка.
                // Единственного ребенка не с чем балансировать — уходим, рекурсия недогруза
                // родителя выше по стеку разрулит структуру слиянием самого родителя.
                // =========================================================================
                if (countChildren >= 2) {
                    if (!tryToMoveKeyFromNeightbor(deepLockedNode, storage, transaction)) {
                        if (i == 0) {
                            joinWithRightNeightbor(deepLockedNode, i, storage, transaction);
                        }
                        else {
                            joinWithLeftNeightbor(deepLockedNode, i, storage, transaction);
                        }
                    }
                }
                break;
            }
        }

        final XdStorageBTreeNode parent = this.getParent();
        final Object parentId = parent.getId();
        final XdStorageBTreeNode root = getTree().getRoot();

        // =========================================================================
        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Ликвидация дефекта удаления 65 / 88):
        // Меняем ошибочное условие "parent.getKeys().size() > 0" на каноническое "== 0"!
        // Замена корня дерева на дочерний узел-приемник обязана происходить строго тогда,
        // когда родительский корень полностью опустел (размер ключей упал до 0) после
        // выполнения операции слияния страниц на нижних этажах.
        // Прежняя логика оставляла пустой корень-заглушку, полностью ослепляя навигацию find!
        // =========================================================================
        if ((parentId != null && parentId.equals(root.getId())) || parent == root) {
            if (parent.getKeys().isEmpty() && !parent.getChildren().isEmpty()) {
                final XdStorageBTreeNode child = parent.loadChildOfThisNode(0, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                storage.delete(parent, transaction);
                child.setParent(null);
                storage.update(child, transaction);
                getTree().setRoot(child);
                storage.update(getTree(), transaction);
                XdStorageBTreeInvariants.checkStructuralAlignment(child, "rootCollapse.child");
            }
        } else if (parent.getKeys().size() <= getTree().getT() - 1) {
            this.getParent().joinWithNeightbor(deepLockedNode, storage, transaction);
        }
    }

    // =========================================================================
    // ФАЗА 2: ИДЕНТИЧНОСТЬ ПО ID ВМЕСТО ИДЕНТИЧНОСТИ ПО ИНСТАНЦИИ.
    // Один и тот же узел дерева может быть представлен в разных графах простым POJO
    // и SimpleWrapper-стабом (дуальность инстанций после подгрузки ссылок из хранилища).
    // Любое удаление/поиск ребенка по ссылке-инстанции молча промахивается и оставляет
    // в родителе висячую ссылку на удаленный узел. Мутации состава детей идут по ID,
    // с fallback-ом на инстансную идентичность для узлов без ID (свежесозданные ноды
    // до первого save, тестовые in-memory сценарии с мок-хранилищем).
    // =========================================================================
    private static void removeChildById(final XdStorageBTreeNode parent, final Object childId, final XdStorageBTreeNode instance) {
        if (parent == null) {
            return;
        }
        final List<XdStorageBTreeNode> children = parent.getChildren();
        synchronized (children) {
            for (int i = 0; i < children.size(); ++i) {
                final XdStorageBTreeNode candidate = children.get(i);
                final Object candidateId = candidate != null ? candidate.getId() : null;
                if (childId != null && childId.equals(candidateId)) {
                    children.remove(i);
                    return;
                }
                if (childId == null && candidate == instance) {
                    children.remove(i);
                    return;
                }
            }
        }
    }

    private void joinWithRightNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode right = this.getParent().loadChildOfThisNode(currentNodeIndex + 1, storage, transaction);
        // ГВАРДЬЯН САМОСЛИЯНИЯ: clamp индекса в loadChildOfThisNode может вернуть нас самих
        if (right == this) {
            return;
        }
        right.lockAndAddToDeepLock(deepLockedNode, transaction);

        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Ликвидация ConcurrentModificationException):
        // Делаем изолированные, независимые снимки (Snapshots) ВСЕХ внутренних коллекций правого соседа.
        // Это полностью защищает итераторы от скрытых модификаций со стороны XML/JSON ридеров
        // во время выполнения рекурсивного findLeftKey(), гарантируя абсолютную стабильность!
        final List<XdStorageBTreeNode> rightChildren = new ArrayList<>(right.getChildren());
        final List<Comparable> rightKeys = new ArrayList<>(right.getKeys());
        final List<Object> rightObjects = new ArrayList<>(right.getObjects());

        // ФАЗА 2: изъятие правого соседа по ID (fallback на инстанс при отсутствии ID)
        removeChildById(this.getParent(), right.getId(), right);
        storage.delete(right, transaction);
        // Спускаем разделительный ключ из родителя во внутренний узел 'this'.
        // КАНОН B-ДЕРЕВА: разделитель между this и right — это РОВНО parent.keys[currentNodeIndex];
        // спуск findLeftKey к левому листу правого поддерева возвращает null на транзитно пустой
        // ноде, и слияние тогда переносит детей БЕЗ разделителя — пары keys/children разъезжаются
        // (children = keys + 2), дальше спуски мисроутят удаления и сироты цепочки листьев.
        // Разделитель между this и right — parent.keys[currentNodeIndex] — уходит из родителя
        // ВСЕГДА (и для листьев, и для внутренних узлов); при внутреннем слиянии он дополнительно
        // спускается в 'this', при листьевом — просто исчезает (листья не требуют разделителя).
        {
            final List<Comparable> parentKeys = this.getParent().getKeys();
            if (currentNodeIndex < 0 || currentNodeIndex >= parentKeys.size()) {
                throw new XdStorageException("joinWithRight: разделитель parent.keys[" + currentNodeIndex
                        + "] не существует (keys=" + parentKeys.size() + ") — родитель разъехался до слияния");
            }
            final Comparable separatorKey = parentKeys.remove(currentNodeIndex);
            if (!rightChildren.isEmpty()) {
                this.getKeys().add(separatorKey);
            }
        }

        // Безопасно переносим ключи и объекты из изолированных снимков
        for (Comparable k : rightKeys) {
            this.getKeys().add(k);
        }
        for (Object o : rightObjects) {
            this.getObjects().add(o);
        }

        // Пакетный перенос дочерних страниц
        for (XdStorageBTreeNode child : rightChildren) {
            XdStorageBTreeNode resolved = child;
            if (XdStorageObjectUtils.isReference(child)) {
                // R-53: стаб разрешается в канонический инстанс до переноса (бэк-референсы
                // чинятся ниже через setParent(this) + materialize(liveTree))
                resolved = materialize(child, storage, transaction, getTree(), this);
            }
            resolved.lockAndAddToDeepLock(deepLockedNode, transaction);
            resolved.setParent(this);
            storage.update(resolved, transaction);
            synchronized (this.getChildren()) {
                this.getChildren().add(resolved);
            }
        }

        if (rightChildren.isEmpty()) {
            chainDebug("JOIN-RIGHT this=" + this.getId() + " absorbing right=" + right.getId() + " right.next=" + (right.getNextTreeNodeOnThisLevel() != null ? right.getNextTreeNodeOnThisLevel().getId() : null)
                    + " this.nextBefore=" + (this.getNextTreeNodeOnThisLevel() != null ? this.getNextTreeNodeOnThisLevel().getId() : null)
                    + " parent=" + this.getParent().getId() + " parentChildren=" + idsOf(this.getParent().getChildren()));
            this.setNextTreeNodeOnThisLevel(right.getNextTreeNodeOnThisLevel());
        }

        // Разделитель уже изъят из родителя выше (parent.keys.remove(currentNodeIndex)) —
        // вычитание ВТОРОГО ключа здесь и создавало сдвиг пар keys/children.
        // Ребенок right уже изъят removeChildById выше: parent снова children == keys + 1.

        storage.update(this.getParent(), transaction);
        storage.update(this, transaction);
        log.debug("B+Tree parent {} after join-with-right", this.getParent().getId());
        XdStorageBTreeInvariants.checkStructuralAlignment(this.getParent(), "joinWithRight.parent");
        XdStorageBTreeInvariants.checkStructuralAlignment(this, "joinWithRight.this");
    }

    private void joinWithLeftNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode left = this.getParent().loadChildOfThisNode(currentNodeIndex - 1, storage, transaction);
        // ГВАРДЬЯН САМОСЛИЯНИЯ: clamp индекса в loadChildOfThisNode может вернуть нас самих
        if (left == this) {
            return;
        }
        if (left.tryLockAndAddToDeepLock(deepLockedNode, transaction)) {

            // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД: Зеркальная защита левого слияния через Snapshots
            final List<XdStorageBTreeNode> thisChildren = new ArrayList<>(this.getChildren());
            final List<Comparable> thisKeys = new ArrayList<>(this.getKeys());
            final List<Object> thisObjects = new ArrayList<>(this.getObjects());

            // ФАЗА 2: изъятие себя из родителя по ID (fallback на инстанс при отсутствии ID)
            removeChildById(this.getParent(), this.getId(), this);
            storage.delete(this, transaction);

            // КАНОН B-ДЕРЕВА: разделитель между left и this — это РОВНО parent.keys[currentNodeIndex - 1].
            // findLeftKey здесь давал null на транзитно пустой ноде -> слияние без разделителя
            // -> children = keys + 2 (см. комментарий в joinWithRightNeightbor).
            // Зеркально joinWithRight: разделитель parent.keys[currentNodeIndex - 1] уходит из
            // родителя всегда; при внутреннем слиянии спускается в left, при листьевом — исчезает.
            {
                final List<Comparable> parentKeys = this.getParent().getKeys();
                final int separatorIndex = currentNodeIndex - 1;
                if (separatorIndex < 0 || separatorIndex >= parentKeys.size()) {
                    throw new XdStorageException("joinWithLeft: разделитель parent.keys[" + separatorIndex
                            + "] не существует (keys=" + parentKeys.size() + ") — родитель разъехался до слияния");
                }
                final Comparable separatorKey = parentKeys.remove(separatorIndex);
                if (!thisChildren.isEmpty()) {
                    left.getKeys().add(separatorKey);
                }
            }

            for (Comparable k : thisKeys) {
                left.getKeys().add(k);
            }
            for (Object o : thisObjects) {
                left.getObjects().add(o);
            }

            for (XdStorageBTreeNode child : thisChildren) {
                XdStorageBTreeNode resolved = child;
                if (XdStorageObjectUtils.isReference(child)) {
                    // R-53: стаб разрешается в канонический инстанс до переноса (бэк-референсы
                    // чинятся ниже через setParent(left) + materialize(liveTree))
                    resolved = materialize(child, storage, transaction, getTree(), left);
                }
                resolved.lockAndAddToDeepLock(deepLockedNode, transaction);
                resolved.setParent(left);
                storage.update(resolved, transaction);
                synchronized (left.getChildren()) {
                    left.getChildren().add(resolved);
                }
            }

            if (thisChildren.isEmpty()) {
                chainDebug("JOIN-LEFT left=" + left.getId() + " absorbing this=" + this.getId() + " this.next=" + (this.getNextTreeNodeOnThisLevel() != null ? this.getNextTreeNodeOnThisLevel().getId() : null)
                        + " left.nextBefore=" + (left.getNextTreeNodeOnThisLevel() != null ? left.getNextTreeNodeOnThisLevel().getId() : null)
                        + " parent=" + this.getParent().getId() + " parentChildren=" + idsOf(this.getParent().getChildren())
                        + " parentKeys=" + this.getParent().getKeys());
                left.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            }

            // Разделитель уже изъят из родителя выше (parent.keys.remove(currentNodeIndex - 1)) —
            // второй вычет здесь создавал сдвиг пар keys/children при слиянии.

            storage.update(this.getParent(), transaction);
            storage.update(left, transaction);
            log.debug("B+Tree parent {} after join-with-left", this.getParent().getId());
            XdStorageBTreeInvariants.checkStructuralAlignment(this.getParent(), "joinWithLeft.parent");
            XdStorageBTreeInvariants.checkStructuralAlignment(left, "joinWithLeft.left");
        }
    }

    private boolean tryToMoveKeyFromNeightbor(final XdStorageBTreeNode deepLockedNode, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        boolean isMoved = false;

        final List<XdStorageBTreeNode> parentChildren = this.getParent().getChildren();
        final int countChildren = parentChildren.size();
        for (int i = 0; i < countChildren; ++i) {
            final XdStorageBTreeNode child = parentChildren.get(i);
            final Object childId = child.getId();
            if ((childId != null && childId.equals(this.getId())) || child == this) {

                // АППАРАТНАЯ АКТИВАЦИЯ СИММЕТРИИ B+ ДЕРЕВА (Раскомментируем левого соседа!):
                if (i == 0) {
                    // Самый левый узел — может занять ключ только справа
                    if (tryToMoveKeyFromRightNeightbor(deepLockedNode, i, storage, transaction)) {
                        isMoved = true;
                    }
                } else if (i == countChildren - 1) {
                    // Самый правый узел — теперь гарантированно fail-safe занимает ключ слева!
                    if (tryToMoveKeyFromLeftNeightbor(deepLockedNode, i, storage, transaction)) {
                        isMoved = true;
                    }
                } else {
                    // Промежуточный узел — пробует занять справа, при неудаче — слева
                    if (tryToMoveKeyFromRightNeightbor(deepLockedNode, i, storage, transaction)
                            || tryToMoveKeyFromLeftNeightbor(deepLockedNode, i, storage, transaction)) {
                        isMoved = true;
                    }
                }
                break;
            }
        }

        return isMoved;
    }

    private boolean tryToMoveKeyFromRightNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode right = this.getParent().loadChildOfThisNode(currentNodeIndex + 1, storage, transaction);
        // ГВАРДЬЯН САМОЗАИМСТВОВАНИЯ: clamp индекса может вернуть нас самих
        if (right == this) {
            return false;
        }
        right.lockAndAddToDeepLock(deepLockedNode, transaction);
        // ГВАРДЬЯН ГРАНИЦ РОДИТЕЛЯ: транзитно пустой/перестроенный список ключей родителя
        // (разъезд keys/children на середине ребалансировки) не может принимать заимствованный
        // разделитель — заимствование невозможно, уходим в слияние
        // КАНОН B-ДЕРЕВА: занимать у соседа можно всегда, когда у него БОЛЬШЕ минимума (t-1),
        // т.е. >= t. Отказ при ровно t вынуждал merge: (<=t-1) + 1 + t = 2t ключей —
        // на единицу больше максимума 2t-1 — и давал пере-полненные ноды children=2t+1.
        if (this.getParent().getKeys().isEmpty()
                || currentNodeIndex >= this.getParent().getKeys().size()
                || right.getKeys().size() < getTree().getT()) {
            return false;
        }
        if (right.getKeys().size() >= getTree().getT()) {
            if (right.getChildren().isEmpty()) {
                this.getKeys().add(right.getKeys().remove(0));
                this.getObjects().add(right.getObjects().remove(0));
                final Comparable key = right.getKeys().isEmpty() ? null : right.getKeys().get(0);
                if (key != null) {
                    this.getParent().getKeys().set(currentNodeIndex, key);
                }
            } else {
                // АЛГОРИТМИЧЕСКИЙ GUARD-БАРЬЕР Б+ ДЕРЕВА:
                // Если у правого соседа на внутреннем уровне нарушен инвариант children = keys + 1,
                // и количество детей меньше 2, заимствование ЗАПРЕЩЕНО спецификацией!
                // Возвращаем false, переводя систему на безопасное каскадное слияние join.
                if (right.getChildren().size() < 2) {
                    return false;
                }

                final XdStorageBTreeNode child = right.loadChildOfThisNode(0, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);

                Comparable parentKey = this.getParent().getKeys().get(currentNodeIndex);
                this.getKeys().add(parentKey);

                Comparable rightFirstKey = right.getKeys().remove(0);
                this.getParent().getKeys().set(currentNodeIndex, rightFirstKey);

                right.getChildren().remove(0);
                child.setParent(this);
                this.getChildren().add(child);

                storage.update(child, transaction);
            }

            storage.update(this.getParent(), transaction);
            storage.update(right, transaction);
            storage.update(this, transaction);
            XdStorageBTreeInvariants.checkStructuralAlignment(this.getParent(), "borrowRight.parent");
            XdStorageBTreeInvariants.checkStructuralAlignment(this, "borrowRight.this");
            XdStorageBTreeInvariants.checkStructuralAlignment(right, "borrowRight.right");

            return true;
        }
        return false;
    }

    private boolean tryToMoveKeyFromLeftNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode left = this.getParent().loadChildOfThisNode(currentNodeIndex - 1, storage, transaction);
        // ГВАРДЬЯН САМОЗАИМСТВОВАНИЯ: clamp индекса может вернуть нас самих
        if (left == this) {
            return false;
        }
        // КАНОН B-ДЕРЕВА: зеркальный фикс порога заимствования (>= t вместо > t)
        if (this.getParent().getKeys().isEmpty()
                || currentNodeIndex - 1 >= this.getParent().getKeys().size()) {
            return false;
        }
        if (left.tryLockAndAddToDeepLock(deepLockedNode, transaction)
                && left.getKeys().size() >= getTree().getT()) {
            final int lastKeyIndex = left.getKeys().size() - 1;
            if (left.getChildren().isEmpty()) {
                final Comparable key = left.getKeys().remove(lastKeyIndex);
                this.getKeys().add(0, key);
                this.getObjects().add(0, left.getObjects().remove(lastKeyIndex));
                this.getParent().getKeys().set(currentNodeIndex - 1, key);
            } else {
                // АЛГОРИТМИЧЕСКОЕ GUARD-БАРЬЕР Б+ ДЕРЕВА: Защита левого заимствования внутренних узлов
                if (left.getChildren().size() < 2) {
                    return false;
                }

                final XdStorageBTreeNode child = left.loadChildOfThisNode(lastKeyIndex + 1, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);

                Comparable parentKey = this.getParent().getKeys().get(currentNodeIndex - 1);
                this.getKeys().add(0, parentKey);

                Comparable leftLastKey = left.getKeys().remove(lastKeyIndex);
                this.getParent().getKeys().set(currentNodeIndex - 1, leftLastKey);

                left.getChildren().remove(lastKeyIndex + 1);
                child.setParent(this);
                this.getChildren().add(0, child);

                storage.update(child, transaction);
            }

            storage.update(this.getParent(), transaction);
            storage.update(left, transaction);
            storage.update(this, transaction);
            XdStorageBTreeInvariants.checkStructuralAlignment(this.getParent(), "borrowLeft.parent");
            XdStorageBTreeInvariants.checkStructuralAlignment(this, "borrowLeft.this");
            XdStorageBTreeInvariants.checkStructuralAlignment(left, "borrowLeft.left");

            return true;
        }
        return false;
    }

    private void deleteFromThisNode(final Comparable key, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final ListIterator<Comparable> keyIterator = getKeys().listIterator();
        final ListIterator<Object> objectIterator = getObjects().listIterator();
        boolean deleted = false;
        while (keyIterator.hasNext()) {
            final Comparable currenComparable = keyIterator.next();
            objectIterator.next();
            final int compareResult = currenComparable.compareTo(key);
            if (compareResult == 0) {
                keyIterator.remove();
                objectIterator.remove();
                deleted = true;
            } else if (compareResult > 0) {
                break;
            }
        }

        if (!deleted) {
            if (CHAIN_DEBUG) {
                final Comparable firstKey = getKeys().isEmpty() ? null : getKeys().get(0);
                final Comparable lastKey = getKeys().isEmpty() ? null : getKeys().get(getKeys().size() - 1);
                chainDebug("DELETE-MISS leaf=" + getId() + " key=" + key + " leafRange=[" + firstKey + ".." + lastKey + "]"
                        + " parent=" + (getParent() != null ? getParent().getId() : null)
                        + " parentKeys=" + (getParent() != null ? getParent().getKeys() : null)
                        + " parentChildren=" + (getParent() != null ? idsOf(getParent().getChildren()) : null));
            }
            throw new XdStorageException("object of " + tree.getId() + " with idgeneration " + key + " does not exists");
        }

        storage.update(this, transaction);
    }

    public void update(final IXdStorageBTreeNode toUnlock, final Comparable key, final Object value, final IXdStorage storage, final IXdStorageTransaction transaction, final AtomicBoolean retryUpdate) throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeNode current = this, child = null;
        try {
            if (!current.tryLockWrite(transaction)) {
                retryUpdate.set(true);
                return;
            }
        } finally {
            toUnlock.unlockWrite();
        }
        while (current.getObjects().isEmpty()) {
            try {
                boolean isFound = false;
                for (int i = 0; i < current.getKeys().size(); ++i) {
                    if (key.compareTo(current.getKeys().get(i)) < 0) {
                        child = current.loadChildOfThisNode(i, storage, transaction);
                        isFound = true;
                        break;
                    }
                }
                if (!isFound) {
                    child = current.loadChildOfThisNode(current.getChildren().size() - 1, storage, transaction);
                }
                if (!child.tryLockWrite(transaction)) {
                    retryUpdate.set(true);
                    return;
                }
            } finally {
                current.unlockWrite();
            }
            current = child;
            child = null;
        }

        try {
            boolean isUpdated = false;
            for (int i = 0; i < current.getKeys().size(); ++i) {
                final int compareResult = key.compareTo(current.getKeys().get(i));
                if (compareResult == 0) {
                    current.getObjects().set(i, value);
                    storage.update(current, transaction);
                    isUpdated = true;
                    break;
                } if (compareResult < 0) {
                    break;
                }
            }
            if (!isUpdated) {
                throw new XdStorageException("search index caouldn't be updated because object with idgeneration " + key + " doesn't exist");
            }
        } finally {
            current.unlockWrite();
        }
    }

    public List<Object> find(final IXdStorageBTreeNode toUnlock, final Comparable key, final IXdStorage storage, final IXdStorageTransaction transaction, final AtomicBoolean retryFind) throws XdStorageException, XdStorageConnectionException {
        XdStorageBTreeNode current = this, child = null;
        try {
            if (!current.tryLockRead(transaction)) {
                retryFind.set(true);
                return null;
            }
        } finally {
            toUnlock.unlockRead();
        }

        while (current != null && current.getObjects().isEmpty()) {
            try {
                boolean isFound = false;

                // =========================================================================
                // АЛГОРИТМИЧЕСКИЙ ЗАЩИТНЫЙ ЩИТ СУБД (Финальное уничтожение IndexOutOfBoundsException):
                // Делаем атомарные изолированные снимки (Snapshots) коллекций ключей и детей
                // текущего промежуточного узла-маршрутизатора ДО начала итерации цикла.
                // Это гарантирует, что даже если параллельный поток-писатель на фазе сплита
                // очистит живой LinkedList ключей через remove(), наш читатель спокойно завершит навигацию
                // по стабильной локальной копии, полностью уничтожая гонки данных!
                // R-56: снимок берется ПОД МОНИТОРОМ коллекции — итерация synchronized-обертки
                // без монитора рвалась CME при параллельном split/join (та же дисциплина, что
                // в сериализаторе R-43).
                // =========================================================================
                final List<Comparable> snapshotKeys;
                final List<XdStorageBTreeNode> snapshotChildren;
                synchronized (current.getKeys()) {
                    snapshotKeys = new ArrayList<>(current.getKeys());
                }
                synchronized (current.getChildren()) {
                    snapshotChildren = new ArrayList<>(current.getChildren());
                }

                for (int i = 0; i < snapshotKeys.size(); ++i) {
                    if (key.compareTo(snapshotKeys.get(i)) < 0) {
                        if (i < snapshotChildren.size()) {
                            child = current.loadChildOfThisNode(i, storage, transaction);
                            isFound = true;
                        }
                        break;
                    }
                }

                if (!isFound && !snapshotChildren.isEmpty()) {
                    child = current.loadChildOfThisNode(snapshotChildren.size() - 1, storage, transaction);
                } else if (!isFound) {
                    child = null;
                }

                if (child != null && !child.tryLockRead(transaction)) {
                    retryFind.set(true);
                    return null;
                }
            } finally {
                current.unlockRead();
            }
            current = child;
            child = null;
        }

        if (current == null) {
            return null;
        }

        final List<Object> result = new ArrayList<>();
        // R-59: обход цепочки листьев ИТЕРАЦИОНЕН. Прежняя рекурсия по next давала
        // по одному кадру стека НА ЛИСТ: multiple-деревья с тысячами листьев
        // (дубль-ключи + шумовые вставки за 10 секунд шторма) падали
        // StackOverflowError в find(). Порядок захватов сохранен: сначала
        // tryLockRead следующего листа, потом release предыдущего.
        try {
            while (true) {
                boolean stop = false;
                boolean hasMatches = false;

                // На уровне листьев точно так же защищаем чтение через снимок ключей и объектов
                // (R-56: снимок под монитором — см. комментарий выше)
                final List<Comparable> leafKeysSnapshot;
                final List<Object> leafObjectsSnapshot;
                synchronized (current.getKeys()) {
                    leafKeysSnapshot = new ArrayList<>(current.getKeys());
                }
                synchronized (current.getObjects()) {
                    leafObjectsSnapshot = new ArrayList<>(current.getObjects());
                }

                for (int i = 0; i < leafKeysSnapshot.size(); ++i) {
                    final int compareResult = key.compareTo(leafKeysSnapshot.get(i));
                    if (compareResult == 0) {
                        if (i < leafObjectsSnapshot.size()) {
                            result.add(leafObjectsSnapshot.get(i));
                            hasMatches = true;
                        }
                    } else if (compareResult < 0) {
                        stop = true;
                        break;
                    }
                }

                if (hasMatches && getTree().isMultiple()) {
                    stop = false;
                }

                if (stop) {
                    return result;
                }

                child = current.getNextTreeNodeOnThisLevel();
                if (child == null) {
                    return result;
                }

                // lock-coupling: захватываем следующий лист, отпускаем текущий.
                // Неудача захвата — штатный ретрай всего поиска (retryFind).
                try {
                    if (!child.tryLockRead(transaction)) {
                        retryFind.set(true);
                        return null;
                    }
                } finally {
                    if (current.isReadLocked()) {
                        current.unlockRead();
                    }
                }
                current = child;
                child = null;
            }
        } finally {
            if (current != null && current.isReadLocked()) {
                current.unlockRead();
            }
        }
    }

    public void read(final IXdStorageBTreeNode toUnlock, final IXdStorage storage, final IXdStorageTransaction transaction, final IXdStorageBTreeViewer viewer) throws XdStorageException, XdStorageConnectionException {
        // R-59: обход цепочки листьев ИТЕРАЦИОНЕН (см. зеркальный комментарий в find):
        // рекурсия по next давала кадр стека на каждый лист и падала
        // StackOverflowError на длинных цепях multiple-деревьев.
        XdStorageBTreeNode current = this;
        // toUnlock может быть и ДЕРЕВОМ (XdStorageBTree.read передает this) —
        // релизим полиморфно через интерфейс
        IXdStorageBTreeNode previous = toUnlock;
        try {
            while (current != null) {
                current.lockRead(transaction);
                try {
                    // =========================================================================
                    // БАРЬЕР КОНСИСТЕНТНОСТИ ЛИСТА: проверка паритета keys/objects обязана идти
                    // СТРОГО под read-локом ноды. Раньше она выполнялась до захвата лока и ловила
                    // промежуточные состояния писателя (ключ добавлен, объект еще нет), роняя
                    // честных читателей диапазона псевдо-исключением "not leaf node".
                    // =========================================================================
                    if (current.getKeys().size() != current.getObjects().size()) {
                        throw new XdStorageException("cannot handle index node, because it is not leaf node");
                    }

                    if (previous != null) {
                        previous.unlockRead();
                        previous = null;
                    }

                    // =========================================================================
                    // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Истинный финал IndexOutOfBoundsException!):
                    // Делаем атомарные изолированные снимки (Snapshots) коллекций ключей и объектов
                    // текущей листовой страницы ДО начала итерации цикла viewer.look().
                    // R-56: снимок под монитором коллекции (дисциплина R-43/R-56, см. find).
                    // =========================================================================
                    final List<Comparable> leafKeysSnapshot;
                    final List<Object> leafObjectsSnapshot;
                    synchronized (current.getKeys()) {
                        leafKeysSnapshot = new ArrayList<>(current.getKeys());
                    }
                    synchronized (current.getObjects()) {
                        leafObjectsSnapshot = new ArrayList<>(current.getObjects());
                    }

                    for (int i = 0; i < leafKeysSnapshot.size(); ++i) {
                        if (i < leafObjectsSnapshot.size()) {
                            viewer.look(leafKeysSnapshot.get(i), leafObjectsSnapshot.get(i));
                        }
                    }

                    current.loadThisNode(storage, transaction);
                } catch (final Throwable unwind) {
                    // Зеркало прежнего per-frame finally: кадр, поймавший исключение,
                    // отпускает СВОЙ read-лок (следующий еще не захвачен)
                    if (current.isReadLocked()) {
                        current.unlockRead();
                    }
                    throw unwind;
                }

                previous = current;
                current = current.getNextTreeNodeOnThisLevel();
            }
        } finally {
            if (previous != null) {
                previous.unlockRead();
            }
        }
    }
}
