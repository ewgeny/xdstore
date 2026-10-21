package io.github.xdstorage.search;

import io.github.xdstorage.IXdStorageWatcher;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.XdStorageTransaction;

import java.util.List;

public interface IXdStorageSearchIndexDaoResource {

    void insert(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void update(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(Object object, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> void watch(XdStorageTransaction transaction, IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException;
}
