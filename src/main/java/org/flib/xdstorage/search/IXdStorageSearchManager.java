package org.flib.xdstorage.search;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.query.IXdStorageCriterion;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.XdStorageTransaction;

import java.util.Collection;

public interface IXdStorageSearchManager {

    boolean hasIndex(Object object);

    void insert(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void insert(Collection<?> objects, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void update(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(Object reference, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(Collection<?> references, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> search(Class<T> cl, String indexName, XdStorageSearchQuery query,
                             XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> search(Class<T> cl, String indexName, XdStorageSqlSearchQuery query,
                             XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;
}
