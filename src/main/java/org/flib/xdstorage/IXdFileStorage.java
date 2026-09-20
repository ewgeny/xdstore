package org.flib.xdstorage;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.structure.update.IXdStorageStructureUpdater;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.Collection;
import java.util.List;

public interface IXdFileStorage extends IXdStorage {

    void registerStructureUpdater(IXdStorageStructureUpdater updater);

    List<IXdStorageStructureUpdater> executeStructureUpdate(Class<?> cl) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> load(Class<T> cl, IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException;

    @SuppressWarnings("unchecked")
    <T> Collection<T> load(Class<T> cl, IXdStoragePredicate<T> predicate, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> load(Class<T> cl, String indexName, XdStorageSearchQuery query) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> load(Class<T> cl, String indexName, XdStorageSearchQuery query,
                           IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> void watch(Class<T> cl, IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException;

    @SuppressWarnings("unchecked")
    <T> void watch(Class<T> cl, IXdStorageWatcher<T> watcher, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;
}
