package org.flib.xdstorage;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.structure.update.IXdStorageStructureUpdater;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageVersion;

import java.util.Collection;
import java.util.List;

public interface IXdFileStorage extends IXdStorage {

    /**
     * A2: the library version (semver, e.g. "2.0.0"), read from the Maven-filtered
     * {@code /xdstorage.properties} resource. "unknown" when the classpath was assembled
     * without Maven filtering.
     */
    default String getVersion() {
        return XdStorageVersion.getVersion();
    }

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
