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
}
