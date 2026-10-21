package io.github.xdstorage;

import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.search.query.XdStorageSearchQuery;
import io.github.xdstorage.structure.update.IXdStorageStructureUpdater;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.utils.XdStorageVersion;

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
