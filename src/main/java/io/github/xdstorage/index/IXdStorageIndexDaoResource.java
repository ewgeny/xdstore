package io.github.xdstorage.index;

import io.github.xdstorage.IXdStoragePredicate;
import io.github.xdstorage.IXdStorageWatcher;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.XdStorageTransaction;

import java.util.Collection;

public interface IXdStorageIndexDaoResource {

    Object getObjectResourceId(Object objectId, XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    boolean has(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> read(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException;

    <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException;

    void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;
}
