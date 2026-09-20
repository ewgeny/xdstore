package org.flib.xdstorage.sqlstorage.fkresource;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.transaction.XdStorageTransaction;

public interface IXdStorageCrossDatasourceFkDaoResource {

    void insert(XdStorageSQLResourceId parentResourceId, Object parentObject, XdStorageSQLResourceId childResourceId, Object childObject, XdStorageTransaction transaction);

    void delete(XdStorageSQLResourceId parentResourceId, Object parentObject, XdStorageSQLResourceId childResourceId, Object childObject, XdStorageTransaction transaction) throws XdStorageException;

}
