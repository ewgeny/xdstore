package io.github.xdstorage.search;

import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.search.query.XdStorageSearchQuery;
import io.github.xdstorage.transaction.XdStorageTransaction;

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

    /**
     * FP-1b (docs/tasks/2.1.0/10): record-level count of the query's snapshot-visible matches.
     * Ignores the query's ORDER BY / LIMIT / OFFSET shaping (SQL COUNT semantics). Every counted
     * record passes the same snapshot-visibility read as a load(), so the number is exactly the
     * size the equivalent search() would return; the objects are read but not retained.
     */
    long count(Class<?> cl, String indexName, XdStorageSearchQuery query,
               XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;
}
