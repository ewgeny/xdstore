package org.flib.xdstorage.sqlstorage.fkresource.reference;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.transaction.XdStorageTransaction;

public interface IXdStorageCrossDatasourceReferenceDaoResource {

    void insert(XdStorageSQLCrossDatasourceFk fk, XdStorageTransaction transaction);

    void delete(XdStorageSQLCrossDatasourceFk fk, XdStorageTransaction transaction) throws XdStorageException;

}
