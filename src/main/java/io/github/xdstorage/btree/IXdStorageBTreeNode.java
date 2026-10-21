package io.github.xdstorage.btree;

import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;

public interface IXdStorageBTreeNode {

    boolean isWriteLocked();

    boolean isReadLocked();

    void lockWrite(IXdStorageTransaction transaction) throws XdStorageException;

    void lockRead(IXdStorageTransaction transaction) throws XdStorageException;

    boolean tryLockWrite(IXdStorageTransaction transaction);

    boolean tryLockRead(IXdStorageTransaction transaction);

    void unlockWrite();

    void unlockRead();

}
