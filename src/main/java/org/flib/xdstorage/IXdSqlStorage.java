package org.flib.xdstorage;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.Collection;

public interface IXdSqlStorage extends IXdStorage
{
    <T> Collection<T> load(Class<T> cl, String indexName, XdStorageSqlSearchQuery query) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> load(Class<T> cl, String indexName, XdStorageSqlSearchQuery query,
                           IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;
}
