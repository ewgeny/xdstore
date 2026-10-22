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

    /**
     * FP-1b (docs/tasks/2.1.0/10): record-level count of the query's snapshot-visible matches,
     * without building the result collection. Ignores the query's ORDER BY / LIMIT / OFFSET
     * (SQL COUNT semantics); runs in the current transaction when one is open.
     */
    long count(Class<?> cl, String indexName, XdStorageSearchQuery query) throws XdStorageException, XdStorageConnectionException;

    /**
     * FP-1b: transactional form of {@link #count(Class, String, XdStorageSearchQuery)} — the
     * count reflects exactly the given transaction's snapshot.
     */
    long count(Class<?> cl, String indexName, XdStorageSearchQuery query,
               IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> void watch(Class<T> cl, IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException;

    @SuppressWarnings("unchecked")
    <T> void watch(Class<T> cl, IXdStorageWatcher<T> watcher, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;
}
