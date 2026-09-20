package org.flib.xdstorage.btree;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

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
