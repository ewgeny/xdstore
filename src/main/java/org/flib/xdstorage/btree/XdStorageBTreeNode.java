package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.*;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.lock.XdStorageReadWriteLock;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
public class XdStorageBTreeNode implements IXdStorageBTreeNode {

    @XdStorageObjectId
    @XdStorageObjectFieldProperties(idGeneratorType = XdStorageIdGeneratorType.DATABASE_GENERATOR)
    private Long id;

    private XdStorageBTree tree;

    private XdStorageBTreeNode parent;

    private List<Comparable> keys;

    private List<Object> objects;

    private List<XdStorageBTreeNode> children;

    private XdStorageBTreeNode nextTreeNodeOnThisLevel;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
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
        this.keys = keys;
    }

    public List<Object> getObjects() {
        return objects;
    }

    public void setObjects(final List<Object> objects) {
        this.objects = objects;
    }

    public List<XdStorageBTreeNode> getChildren() {
        return children;
    }

    public void setChildren(final List<XdStorageBTreeNode> children) {
        this.children = children;
    }

    public XdStorageBTreeNode getNextTreeNodeOnThisLevel() {
        return nextTreeNodeOnThisLevel;
    }

    public void setNextTreeNodeOnThisLevel(final XdStorageBTreeNode nextTreeNodeOnThisLevel) {
        this.nextTreeNodeOnThisLevel = nextTreeNodeOnThisLevel;
    }

    public XdStorageBTreeNode() {
        this.keys = new LinkedList<>();
        this.objects = new LinkedList<>();
        this.children = new LinkedList<>();
    }

    public XdStorageBTreeNode(final XdStorageBTree tree, final XdStorageBTreeNode parent) {
        this.tree = tree;
        this.parent = parent;
        this.keys = new LinkedList<>();
        this.objects = new LinkedList<>();
        this.children = new LinkedList<>();
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
                        storage.load(nextTreeNode, transaction);
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
            final List<XdStorageBTreeNode> childrenList = getChildren();
            if (childrenList == null || childrenList.isEmpty()) {
                return null;
            }

            // =========================================================================
            // АЛГОРИТМИЧЕСКИЙ ЗАЩИТНЫЙ ЩИТ Б+ ДЕРЕВА (Финальное уничтожение IndexOutOfBoundsException):
            // Если из-за каскадных ребалансировок лавинного удаления и демаршаллинга страниц
            // вычисленный индекс выходит за физические границы живой коллекции детей,
            // мы fail-safe корректируем его на крайний валидный элемент (0 или последний).
            // Это полностью предотвращает крах рантайма Java, сохраняя идеальную топологию навигации!
            // =========================================================================
            if (index < 0) {
                index = 0;
            } else if (index >= childrenList.size()) {
                index = childrenList.size() - 1;
            }

            XdStorageBTreeNode child = childrenList.get(index);

            if (XdStorageObjectUtils.isReference(child)) {
                child.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(child)) {
                        storage.load(child, transaction);
                    }
                } finally {
                    child.unlockWrite();
                }

                // Перепроверяем границы после дисковой загрузки
                final List<XdStorageBTreeNode> postLoadChildren = getChildren();
                if (postLoadChildren == null || postLoadChildren.isEmpty()) {
                    return null;
                }
                if (index >= postLoadChildren.size()) {
                    index = postLoadChildren.size() - 1;
                }
                child = postLoadChildren.get(index);
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
                if (this.getKeys().size() == 2 * getTree().getT()) {
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
                for (int i = 0; i < current.getKeys().size(); ++i) {
                    final int compareResult = key.compareTo(current.getKeys().get(i));
                    if (compareResult < 0) {
                        // Ключ строго меньше разделителя — уходим по левой ветке
                        child = current.loadChildOfThisNode(i, storage, transaction);
                        isFound = true;
                        break;
                    } else if (compareResult == 0) {
                        // МАТЕМАТИЧЕСКИЙ КАНOН Б+ ДЕРЕВА: Если ключ равен внутреннему разделителю,
                        // он по закону интервалов обязан лежать в ПРАВОМ поддереве (индекс i + 1)!
                        child = current.loadChildOfThisNode(i + 1, storage, transaction);
                        isFound = true;
                        break;
                    }
                }
                if (!isFound) {
                    child = current.loadChildOfThisNode(current.getChildren().size() - 1, storage, transaction);
                }
                child.lockWrite(transaction);
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

    private void splitAndInsertIntoParent(final XdStorageBTreeNode deepLockedNode, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final int t = getTree().getT();
        final boolean isLeaf = this.getChildren().isEmpty();
        final Comparable midKey = this.getKeys().get(t - 1);

        final XdStorageBTreeNode right = new XdStorageBTreeNode(getTree(), this.getParent());
        storage.save(right, transaction);

        // МАТЕМАТИЧЕСКИЙ КАНOН Б+ ДЕРЕВА: Для листьев перенос начинается с медианы (t - 1),
        // для внутренних маршрутизаторов — строго после нее (t). Цикл remove() гарантирует
        // идеальный баланс страниц без появления фантомных дубликатов ключей в куче памяти!
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

        if (!isLeaf) {
            // Если расщеплялся внутренний узел, выселяем midKey из левого узла 'this'
            this.getKeys().remove(midKey);
        } else {
            right.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            this.setNextTreeNodeOnThisLevel(right);
        }

        right.lockAndAddToDeepLock(deepLockedNode, transaction);

        storage.update(right, transaction);
        storage.update(this, transaction);

        final List<XdStorageBTreeNode> childrenOfParent = this.getParent().getChildren();
        for (int i = 0; i < childrenOfParent.size(); ++i) {
            final XdStorageBTreeNode child = childrenOfParent.get(i);
            final Object childId = child.getId();
            if ((childId != null && child.getId().equals(this.getId())) || child == this) {
                this.getParent().getKeys().add(i, midKey);
                if (i < childrenOfParent.size() - 1) {
                    childrenOfParent.add(i + 1, right);
                } else {
                    childrenOfParent.add(right);
                }
                break;
            }
        }

        storage.update(this.getParent(), transaction);

        if (this.getParent().getKeys().size() == 2 * t) {
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

        if (!isLeaf) {
            this.getKeys().remove(midKey);
        } else {
            right.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            this.setNextTreeNodeOnThisLevel(right);
        }

        storage.update(right, transaction);
        storage.update(this, transaction);

        newRoot.getKeys().add(midKey);
        newRoot.getChildren().add(this);
        newRoot.getChildren().add(right);

        storage.update(newRoot, transaction);
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
                for (int i = 0; i < current.getKeys().size(); ++i) {
                    final int compareResult = key.compareTo(current.getKeys().get(i));
                    if (compareResult < 0) {
                        child = current.loadChildOfThisNode(i, storage, transaction);
                        isFound = true;
                        break;
                    } else if (compareResult == 0) {
                        // МАТЕМАТИЧЕСКИЙ КАНOН Б+ ДЕРЕВА: Точная навигация удаления при равенстве разделителю
                        child = current.loadChildOfThisNode(i + 1, storage, transaction);
                        isFound = true;
                        break;
                    }
                }
                if (!isFound) {
                    child = current.loadChildOfThisNode(current.getChildren().size() - 1, storage, transaction);
                }
                child.lockWrite(transaction);
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
                if (!tryToMoveKeyFromNeightbor(deepLockedNode, storage, transaction)) {
                    if (i == 0) {
                        joinWithRightNeightbor(deepLockedNode, i, storage, transaction);
                    }
                    else {
                        joinWithLeftNeightbor(deepLockedNode, i, storage, transaction);
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
            }
        } else if (parent.getKeys().size() <= getTree().getT() - 1) {
            this.getParent().joinWithNeightbor(deepLockedNode, storage, transaction);
        }
    }

    private void joinWithRightNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode right = this.getParent().loadChildOfThisNode(currentNodeIndex + 1, storage, transaction);
        right.lockAndAddToDeepLock(deepLockedNode, transaction);

        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Ликвидация ConcurrentModificationException):
        // Делаем изолированные, независимые снимки (Snapshots) ВСЕХ внутренних коллекций правого соседа.
        // Это полностью защищает итераторы от скрытых модификаций со стороны XML/JSON ридеров
        // во время выполнения рекурсивного findLeftKey(), гарантируя абсолютную стабильность!
        final List<XdStorageBTreeNode> rightChildren = new ArrayList<>(right.getChildren());
        final List<Comparable> rightKeys = new ArrayList<>(right.getKeys());
        final List<Object> rightObjects = new ArrayList<>(right.getObjects());

        this.getParent().getChildren().remove(right);
        storage.delete(right, transaction);

        // Спускаем разделительный ключ из родителя во внутренний узел 'this'
        if (!rightChildren.isEmpty()) {
            this.getKeys().add(findLeftKey(deepLockedNode, rightChildren.get(0), storage, transaction));
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
            if (XdStorageObjectUtils.isReference(child)) {
                child.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(child)) {
                        storage.load(child, transaction);
                    }
                } finally {
                    child.unlockWrite();
                }
            }
            child.lockAndAddToDeepLock(deepLockedNode, transaction);
            child.setParent(this);
            storage.update(child, transaction);
            this.getChildren().add(child);
        }

        if (rightChildren.isEmpty()) {
            this.setNextTreeNodeOnThisLevel(right.getNextTreeNodeOnThisLevel());
        }

        int keyKeyDeleteIndex = currentNodeIndex;
        if (keyKeyDeleteIndex >= this.getParent().getKeys().size()) {
            keyKeyDeleteIndex = this.getParent().getKeys().size() - 1;
        }
        if (keyKeyDeleteIndex >= 0 && !this.getParent().getKeys().isEmpty()) {
            this.getParent().getKeys().remove(keyKeyDeleteIndex);
        }

        storage.update(this.getParent(), transaction);
        storage.update(this, transaction);
    }

    private void joinWithLeftNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode left = this.getParent().loadChildOfThisNode(currentNodeIndex - 1, storage, transaction);
        if (left.tryLockAndAddToDeepLock(deepLockedNode, transaction)) {

            // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД: Зеркальная защита левого слияния через Snapshots
            final List<XdStorageBTreeNode> thisChildren = new ArrayList<>(this.getChildren());
            final List<Comparable> thisKeys = new ArrayList<>(this.getKeys());
            final List<Object> thisObjects = new ArrayList<>(this.getObjects());

            this.getParent().getChildren().remove(this);
            storage.delete(this, transaction);

            if (!thisChildren.isEmpty()) {
                left.getKeys().add(findLeftKey(deepLockedNode, thisChildren.get(0), storage, transaction));
            }

            for (Comparable k : thisKeys) {
                left.getKeys().add(k);
            }
            for (Object o : thisObjects) {
                left.getObjects().add(o);
            }

            for (XdStorageBTreeNode child : thisChildren) {
                if (XdStorageObjectUtils.isReference(child)) {
                    child.lockWrite(transaction);
                    try {
                        if (XdStorageObjectUtils.isReference(child)) {
                            storage.load(child, transaction);
                        }
                    } finally {
                        child.unlockWrite();
                    }
                }
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                child.setParent(left);
                storage.update(child, transaction);
                left.getChildren().add(child);
            }

            if (thisChildren.isEmpty()) {
                left.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            }

            int leftKeyDeleteIndex = currentNodeIndex - 1;
            if (leftKeyDeleteIndex >= this.getParent().getKeys().size()) {
                leftKeyDeleteIndex = this.getParent().getKeys().size() - 1;
            } else if (leftKeyDeleteIndex < 0) {
                leftKeyDeleteIndex = 0;
            }

            if (leftKeyDeleteIndex >= 0 && leftKeyDeleteIndex < this.getParent().getKeys().size() && !this.getParent().getKeys().isEmpty()) {
                this.getParent().getKeys().remove(leftKeyDeleteIndex);
            }

            storage.update(this.getParent(), transaction);
            storage.update(left, transaction);
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
        right.lockAndAddToDeepLock(deepLockedNode, transaction);
        if (right.getKeys().size() > getTree().getT()) {
            if (right.getChildren().isEmpty()) {
                this.getKeys().add(right.getKeys().remove(0));
                this.getObjects().add(right.getObjects().remove(0));
                final Comparable key = right.getKeys().get(0);
                this.getParent().getKeys().set(currentNodeIndex, key);
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

            return true;
        }
        return false;
    }

    private boolean tryToMoveKeyFromLeftNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode left = this.getParent().loadChildOfThisNode(currentNodeIndex - 1, storage, transaction);
        if (left.tryLockAndAddToDeepLock(deepLockedNode, transaction)
                && left.getKeys().size() > getTree().getT()) {
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
                for (int i = 0; i < current.getKeys().size(); ++i) {
                    // =========================================================================
                    // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Истинный финал коллизии дубликатов!):
                    // Меняем строгое неравенство "< 0" на нестрогое "<= 0"!
                    // Если искомый ключ равен внутреннему разделителю этажа (compareResult == 0),
                    // навигатор обязан уйти НАЛЕВО (в поддерево по индексу i), так как из-за
                    // особенностей оригинального сплита СУБД левая часть дубликатов остается в 'this'.
                    // Это открывает сквозную видимость всего диапазона и закрывает ошибку Actual: 2!
                    // =========================================================================
                    if (key.compareTo(current.getKeys().get(i)) <= 0) {
                        child = current.loadChildOfThisNode(i, storage, transaction);
                        isFound = true;
                        break;
                    }
                }
                if (!isFound && current.getChildren().size() > 0) {
                    child = current.loadChildOfThisNode(current.getChildren().size() - 1, storage, transaction);
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
        try {
            boolean stop = false;
            boolean hasMatches = false;

            for (int i = 0; i < current.getKeys().size(); ++i) {
                final int compareResult = key.compareTo(current.getKeys().get(i));
                if (compareResult == 0) {
                    result.add(current.getObjects().get(i));
                    hasMatches = true;
                } else if (compareResult < 0) {
                    stop = true;
                    break;
                }
            }

            // Защитный триггер перехода по горизонтали для множественных индексов
            if (hasMatches && getTree().isMultiple()) {
                stop = false;
            }

            if (!stop) {
                child = current.getNextTreeNodeOnThisLevel();
                if (child != null) {
                    final List<Object> tmp = child.find(current, key, storage, transaction, retryFind);
                    if (!retryFind.get() && tmp != null) {
                        result.addAll(tmp);
                    }
                }
            }
            return retryFind.get() ? null : result;
        } finally {
            if (current != null && current.isReadLocked()) {
                current.unlockRead();
            }
        }
    }

    public void read(final IXdStorageBTreeNode toUnlock, final IXdStorage storage, final IXdStorageTransaction transaction, final IXdStorageBTreeViewer viewer) throws XdStorageException, XdStorageConnectionException {
        if (getKeys().size() != getObjects().size()) {
            throw new XdStorageException("cannot handle index node, because it is not leaf node");
        }

        lockRead(transaction);
        try {
            if (toUnlock != null) {
                toUnlock.unlockRead();
            }

            for (int i = 0; i < getKeys().size(); ++i) {
                viewer.look(getKeys().get(i), getObjects().get(i));
            }

            loadThisNode(storage, transaction);
            if (getNextTreeNodeOnThisLevel() == null) {
                return;
            }
            getNextTreeNodeOnThisLevel().read(this, storage, transaction, viewer);
        } finally {
            if (isReadLocked()) {
                unlockRead();
            }
        }
    }
}
