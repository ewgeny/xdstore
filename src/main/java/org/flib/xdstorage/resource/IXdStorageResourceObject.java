package org.flib.xdstorage.resource;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;

import java.util.Collection;

public interface IXdStorageResourceObject<DaoInterface> {

    Object getResourceId();

    Class<?> getObjectsClass();

    long getObjectsCount();

    boolean hasChanges(XdStorageTransaction transaction) throws XdStorageException;

    void prepare(XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void rollbackPerformingFirstPhaseCommit(XdStorageTransaction transaction, Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException;

    void performFirstPhaseCommit(XdStorageTransaction transaction, XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException;

    void performSecondPhaseCommit(XdStorageTransaction transaction, XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException;

    void rollback(XdStorageTransaction transaction) throws XdStorageException;

    void lockForCommit(XdStorageTransaction transaction);

    void unlockAfterCommit(XdStorageTransaction transaction);

    void release(XdStorageTransaction transaction);

    DaoInterface getDao();
}
