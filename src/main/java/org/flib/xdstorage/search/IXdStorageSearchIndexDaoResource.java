package org.flib.xdstorage.search;

import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.util.List;

public interface IXdStorageSearchIndexDaoResource {

    void insert(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void update(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> void watch(XdStorageTransaction transaction, IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException;

    List<IXdStorageSimpleWrapper> selectObjectReferences(XdStorageClassInfo clInfo, XdStorageSqlSearchQuery query, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;
}
