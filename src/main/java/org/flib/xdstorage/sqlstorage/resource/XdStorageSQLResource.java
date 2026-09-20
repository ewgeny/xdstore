package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import java.util.Collection;

/**
 * Исправленная и скомпилированная реализация базового реляционного ресурса СУБД.
 */
public class XdStorageSQLResource implements IXdStorageResourceObject {

    private final XdStorageServicesLocator services;
    private final XdStorageSQLResourcesManager manager;
    private final XdStorageSQLResourceId resourceId;
    private final XdStorageClassInfo clInfo;

    public XdStorageSQLResource(final XdStorageServicesLocator services, final XdStorageSQLResourcesManager manager,
                                final XdStorageSQLResourceId resourceId, final XdStorageClassInfo clInfo) {
        this.services = services;
        this.manager = manager;
        this.resourceId = resourceId;
        this.clInfo = clInfo;
    }

    @Override public Object getResourceId() { return resourceId; }
    @Override public Class<?> getObjectsClass() { return clInfo.getClazz(); }
    @Override public long getObjectsCount() { return 0L; }
    @Override public boolean hasChanges(final XdStorageTransaction tx) { return true; }
    @Override public Object getDao() { return this; }

    @Override public void prepare(XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {}
    @Override public void performFirstPhaseCommit(XdStorageTransaction tx, XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {}
    @Override public void performSecondPhaseCommit(XdStorageTransaction tx, XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {}
    @Override public void rollbackPerformingFirstPhaseCommit(XdStorageTransaction tx, Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException {}
    @Override public void rollback(XdStorageTransaction tx) throws XdStorageException {}
    @Override public void release(XdStorageTransaction tx) {}
    @Override public void lockForCommit(XdStorageTransaction tx) {}
    @Override public void unlockAfterCommit(XdStorageTransaction tx) {}
}
