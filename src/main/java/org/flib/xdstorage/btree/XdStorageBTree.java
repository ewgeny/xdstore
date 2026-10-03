package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.lock.XdStorageReadWriteLock;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
public class XdStorageBTree implements IXdStorageBTreeNode {

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
    }

    private final XdStorageReadWriteLock lock = new XdStorageReadWriteLock();

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

            retryInsert.set(false);
            lockWrite(transaction);
            if (root == null) {
                createRoot(storage, transaction);
            }
            root.insert(this, key, object, storage, transaction, retryInsert);
        } while (retryInsert.get());
    }

    private void createRoot(final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
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

            retryDelete.set(false);
            lockWrite(transaction);

            // Защита от фантомного зануления внутриdo-while цикла
            if (root == null && wasInitialized) {
                XdStorageBTree freshTree = storage.load(XdStorageBTree.class, this.id, transaction);
                if (freshTree != null && freshTree.getRoot() != null) {
                    this.root = freshTree.getRoot();
                    this.firstLeaf = freshTree.getFirstLeaf();
                }
            }

            if (root == null) {
                unlockWrite();
                return;
            }
            root.delete(this, key, storage, transaction, retryDelete);
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

            retryUpdate.set(false);
            lockWrite(transaction);

            if (root == null && wasInitialized) {
                XdStorageBTree freshTree = storage.load(XdStorageBTree.class, this.id, transaction);
                if (freshTree != null && freshTree.getRoot() != null) {
                    this.root = freshTree.getRoot();
                    this.firstLeaf = freshTree.getFirstLeaf();
                }
            }

            if (root == null) {
                unlockWrite();
                throw new XdStorageException("object of " + id + " with idgeneration " + key + " does not exists");
            }
            root.update(this, key, value, storage, transaction, retryUpdate);
        } while (retryUpdate.get());
    }

    public List<Object> find(final Comparable key, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        // Восстановление живого корня для операции неблокирующего поиска find
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

        if (root != null && XdStorageObjectUtils.isReference(root)) {
            lockWrite(transaction);
            try {
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
        }

        List<Object> result;
        final AtomicBoolean retryFind = new AtomicBoolean();
        do {
            retryFind.set(false);
            lockRead(transaction);
            if (root == null) {
                unlockRead();
                return Collections.emptyList();
            }
            result = root.find(this, key, storage, transaction, retryFind);
        } while (retryFind.get());
        return result == null ? Collections.emptyList() : result;
    }

    public void read(final IXdStorage storage, final IXdStorageTransaction transaction, final IXdStorageBTreeViewer viewer) throws XdStorageException, XdStorageConnectionException {
        lockWrite(transaction);
        try {
            if (firstLeaf == null) {
                return;
            }

            if (XdStorageObjectUtils.isReference(firstLeaf)) {
                firstLeaf.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(firstLeaf)) {
                        storage.load(firstLeaf, transaction);
                    }
                } finally {
                    firstLeaf.unlockWrite();
                }
            }
        } finally {
            unlockWrite();
        }

        lockRead(transaction);
        if (firstLeaf == null) {
            unlockRead();
            return;
        }
        firstLeaf.read(this, storage, transaction, viewer);
    }
}
