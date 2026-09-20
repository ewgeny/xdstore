package org.flib.xdstorage.sqlstorage.fkresource;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;

import java.util.Collection;

public class XdStorageSQLDummyCrossDatasourceFkDaoResource implements IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource>, IXdStorageCrossDatasourceFkDaoResource {

    private static final XdStorageSQLResourceId RESOURCE_ID = new XdStorageSQLResourceId();

    static
    {
        RESOURCE_ID.setTable("dummy_table");
        RESOURCE_ID.setDataSource("dummy_datasource");
    }


    private static final IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource> Instance = new XdStorageSQLDummyCrossDatasourceFkDaoResource();

    public static IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource> getInstance()
    {
        return Instance;
    }

    @Override
    public Object getResourceId() {
        return RESOURCE_ID;
    }

    @Override
    public Class<?> getObjectsClass() {
        return XdStorageSQLCrossDatasourceFk.class;
    }

    @Override
    public long getObjectsCount() {
        return 0;
    }

    @Override
    public boolean hasChanges(XdStorageTransaction transaction) {
        return false;
    }

    @Override
    public void prepare(XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        // do nothing
    }

    @Override
    public void rollbackPerformingFirstPhaseCommit(XdStorageTransaction transaction, Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException {
        // do nothing
    }

    @Override
    public void performFirstPhaseCommit(XdStorageTransaction transaction, XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        // do nothing
    }

    @Override
    public void performSecondPhaseCommit(XdStorageTransaction transaction, XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        // do nothing
    }

    @Override
    public void rollback(XdStorageTransaction transaction) throws XdStorageException {
        // do nothing
    }

    @Override
    public void lockForCommit(XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public void unlockAfterCommit(XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public IXdStorageCrossDatasourceFkDaoResource getDao() {
        return this;
    }

    @Override
    public void insert(XdStorageSQLResourceId parentResourceId, Object parentObject, XdStorageSQLResourceId childResourceId, Object childObject, XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public void delete(XdStorageSQLResourceId parentResourceId, Object parentObject, XdStorageSQLResourceId childResourceId, Object childObject, XdStorageTransaction transaction) throws XdStorageException {
        // do nothing
    }
}
