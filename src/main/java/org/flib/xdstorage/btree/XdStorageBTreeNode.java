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

    private XdStorageBTreeNode loadChildOfThisNode(final int index, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        lockRead(transaction);
        try {
            final List<XdStorageBTreeNode> children = getChildren();

            final XdStorageBTreeNode child = children.get(index);
            if (XdStorageObjectUtils.isReference(child)) {
                child.lockWrite(transaction);
                try {
                    if (XdStorageObjectUtils.isReference(child)) {
                        storage.load(child, transaction);
                        child.loadThisNode(storage, transaction);
                    }
                } finally {
                    child.unlockWrite();
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
                    if (key.compareTo(current.getKeys().get(i)) < 0) {
                        child = current.loadChildOfThisNode(i, storage, transaction);
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
        final XdStorageBTreeNode right = new XdStorageBTreeNode(getTree(), this.getParent());
        storage.save(right, transaction);
        final ListIterator<Comparable> keyListIterator = getKeys().listIterator(getTree().getT());
        final ListIterator<Object> objectListIterator;
        if (getObjects().isEmpty()) {
            objectListIterator = null;
        } else {
            objectListIterator = getObjects().listIterator(getTree().getT());
        }
        int childrenListIndex;
        if (getChildren().isEmpty()) {
            childrenListIndex = -1;
        } else {
            childrenListIndex = getTree().getT();
        }
        while (keyListIterator.hasNext()) {
            right.getKeys().add(keyListIterator.next());
            keyListIterator.remove();
            if (objectListIterator != null) {
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

        if (!this.getObjects().isEmpty()) {
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
                this.getParent().getKeys().add(i, findLeftKey(deepLockedNode, right, storage, transaction));
                if (i < childrenOfParent.size() - 1) {
                    childrenOfParent.add(i + 1, right);
                } else {
                    childrenOfParent.add(right);
                }
                break;
            }
        }

        storage.update(this.getParent(), transaction);

        if (this.getParent().getKeys().size() == 2 * getTree().getT()) {
            if (this.getParent().getParent() == null) {
                this.getParent().splitAndCreateNewRoot(deepLockedNode, storage, transaction);
            } else {
                this.getParent().splitAndInsertIntoParent(deepLockedNode, storage, transaction);
            }
        }
    }

    private void splitAndCreateNewRoot(final XdStorageBTreeNode deepLockedNode, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode newRoot = new XdStorageBTreeNode(getTree(), null);
        storage.save(newRoot, transaction);

        newRoot.lockAndAddToDeepLock(deepLockedNode, transaction);

        getTree().setRoot(newRoot);
        storage.update(getTree(), transaction);

        this.setParent(getTree().getRoot());
        final XdStorageBTreeNode right = new XdStorageBTreeNode(getTree(), this.getParent());
        storage.save(right, transaction);

        right.lockAndAddToDeepLock(deepLockedNode, transaction);

        final ListIterator<Comparable> keyListIterator = getKeys().listIterator(getTree().getT());
        final ListIterator<Object> objectListIterator;
        if (getObjects().isEmpty()) {
            objectListIterator = null;
        } else {
            objectListIterator = getObjects().listIterator(getTree().getT());
        }
        int childrenListIndex;
        if (getChildren().isEmpty()) {
            childrenListIndex = -1;
        } else {
            childrenListIndex = getTree().getT();
        }
        while (keyListIterator.hasNext()) {
            right.getKeys().add(keyListIterator.next());
            keyListIterator.remove();
            if (objectListIterator != null) {
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

        if (!this.getObjects().isEmpty()) {
            right.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            this.setNextTreeNodeOnThisLevel(right);
        }

        storage.update(right, transaction);
        storage.update(this, transaction);

        newRoot.getKeys().add(findLeftKey(deepLockedNode, right, storage, transaction));
        newRoot.getChildren().add(this);
        newRoot.getChildren().add(right);

        storage.update(newRoot, transaction);
    }

    private void insertIntoThisNode(final Comparable key, final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        boolean isInserted = false;
        int i;
        for (i = 0; i < getKeys().size(); ++i) {
            final int compareResult = key.compareTo(getKeys().get(i));
            if (getTree().isMultiple() && compareResult == 0) {
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
                final XdStorageBTreeNode root = getTree().getRoot();
                final Object rootId = root.getId();
                if (getKeys().size() == 0 &&
                        ( (rootId != null && rootId.equals(this.getId())) || root == this )) {
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
                    if (key.compareTo(current.getKeys().get(i)) < 0) {
                        child = current.loadChildOfThisNode(i, storage, transaction);
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
//                    if (i != countChildren - 1) {
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
        if ((parentId != null && parentId.equals(root.getId())) || parent == root) {
            if (parent.getKeys().size() == 0) {
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
        this.getParent().getChildren().remove(currentNodeIndex + 1);
        storage.delete(right, transaction);

        final ListIterator<Comparable> keyListIterator = right.getKeys().listIterator();
        final ListIterator<Object> objectListIterator;
        if (right.getObjects().isEmpty()) {
            objectListIterator = null;
        } else {
            objectListIterator = right.getObjects().listIterator();
        }
        int childrenListIndex;
        if (right.getChildren().isEmpty()) {
            childrenListIndex = -1;
        } else {
            childrenListIndex = 0;
            this.getKeys().add(findLeftKey(deepLockedNode, right.loadChildOfThisNode(0, storage, transaction), storage, transaction));
        }
        while (keyListIterator.hasNext()) {
            this.getKeys().add(keyListIterator.next());
            if (objectListIterator != null) {
                this.getObjects().add(objectListIterator.next());
            }
            if (childrenListIndex != -1) {
                final XdStorageBTreeNode child = right.loadChildOfThisNode(childrenListIndex, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                right.getChildren().remove(childrenListIndex);
                child.setParent(this);
                storage.update(child, transaction);
                this.getChildren().add(child);
            }
        }
        if (childrenListIndex != -1) {
            final XdStorageBTreeNode child = right.loadChildOfThisNode(childrenListIndex, storage, transaction);
            child.lockAndAddToDeepLock(deepLockedNode, transaction);
            right.getChildren().remove(childrenListIndex);
            child.setParent(this);
            storage.update(child, transaction);
            this.getChildren().add(child);
        } else {
            this.setNextTreeNodeOnThisLevel(right.getNextTreeNodeOnThisLevel());
        }
        this.getParent().getKeys().remove(currentNodeIndex);

        storage.update(this.getParent(), transaction);
        storage.update(this, transaction);
    }

    private void joinWithLeftNeightbor(final XdStorageBTreeNode deepLockedNode, final int currentNodeIndex, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageBTreeNode left = this.getParent().loadChildOfThisNode(currentNodeIndex - 1, storage, transaction);
        if (left.tryLockAndAddToDeepLock(deepLockedNode, transaction)) {
            storage.delete(this, transaction);

            final ListIterator<Comparable> keyListIterator = this.getKeys().listIterator();
            final ListIterator<Object> objectListIterator;
            if (this.getObjects().isEmpty()) {
                objectListIterator = null;
            } else {
                objectListIterator = this.getObjects().listIterator();
            }
            int childrenListIndex;
            if (this.getChildren().isEmpty()) {
                childrenListIndex = -1;
            } else {
                childrenListIndex = 0;
                left.getKeys().add(findLeftKey(deepLockedNode, this.loadChildOfThisNode(0, storage, transaction), storage, transaction));
            }
            while (keyListIterator.hasNext()) {
                left.getKeys().add(keyListIterator.next());
                if (objectListIterator != null) {
                    left.getObjects().add(objectListIterator.next());
                }
                if (childrenListIndex != -1) {
                    final XdStorageBTreeNode child = loadChildOfThisNode(childrenListIndex, storage, transaction);
                    child.lockAndAddToDeepLock(deepLockedNode, transaction);
                    getChildren().remove(childrenListIndex);
                    child.setParent(left);
                    storage.update(child, transaction);
                    left.getChildren().add(child);
                }
            }
            if (childrenListIndex != -1) {
                final XdStorageBTreeNode child = loadChildOfThisNode(childrenListIndex, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                getChildren().remove(childrenListIndex);
                child.setParent(left);
                storage.update(child, transaction);
                left.getChildren().add(child);
            } else {
                left.setNextTreeNodeOnThisLevel(this.getNextTreeNodeOnThisLevel());
            }
            this.getParent().getKeys().remove(currentNodeIndex - 1);
            this.getParent().getChildren().remove(currentNodeIndex);

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
//                if (i == 0) {
                if (i != countChildren - 1) {
                    if (tryToMoveKeyFromRightNeightbor(deepLockedNode, i, storage, transaction)) {
                        isMoved = true;
                    }
                }
//                else if (i == countChildren - 1) {
//                    if (tryToMoveKeyFromLeftNeightbor(deepLockedNode, i, storage, transaction)) {
//                        isMoved = true;
//                    }
//                } else {
//                    if (tryToMoveKeyFromRightNeightbor(deepLockedNode, i, storage, transaction)
//                            && tryToMoveKeyFromLeftNeightbor(deepLockedNode, i, storage, transaction)) {
//                        isMoved = true;
//                    }
//                }
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
                final XdStorageBTreeNode child = right.loadChildOfThisNode(0, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                right.getKeys().remove(0);
                right.getChildren().remove(0);
                child.setParent(this);
                this.getKeys().add(findLeftKey(deepLockedNode, child, storage, transaction));
                this.getChildren().add(child);
                this.getParent().getKeys().set(currentNodeIndex, findLeftKey(deepLockedNode, right, storage, transaction));

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
                final XdStorageBTreeNode child = left.loadChildOfThisNode(lastKeyIndex + 1, storage, transaction);
                child.lockAndAddToDeepLock(deepLockedNode, transaction);
                left.getKeys().remove(lastKeyIndex);
                left.getChildren().remove(lastKeyIndex + 1);
                child.setParent(this);
                this.getKeys().add(0, findLeftKey(deepLockedNode, this.loadChildOfThisNode(0, storage, transaction), storage, transaction));
                this.getChildren().add(0, child);
                getParent().getKeys().set(currentNodeIndex - 1, findLeftKey(deepLockedNode, child, storage, transaction));

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

        final List<Object> result = new ArrayList<>();
        try {
            boolean stop = false;
            for (int i = 0; i < current.getKeys().size(); ++i) {
                final int compareResult = key.compareTo(current.getKeys().get(i));
                if (compareResult == 0) {
                    result.add(current.getObjects().get(i));
                } else if (compareResult < 0) {
                    stop = true;
                    break;
                }
            }
            if (!stop) {
                child = current.getNextTreeNodeOnThisLevel();
                if (child != null) {
                    final List<Object> tmp = child.find(current, key, storage, transaction, retryFind);
                    if (!retryFind.get()) {
                        result.addAll(tmp);
                    }
                }
            }
            return retryFind.get() ? null : result;
        } finally {
            if (current.isReadLocked()) {
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
