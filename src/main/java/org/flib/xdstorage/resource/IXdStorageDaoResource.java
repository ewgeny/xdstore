package org.flib.xdstorage.resource;

import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.transaction.XdStorageTransaction;

import java.util.Collection;

public interface IXdStorageDaoResource {

    Object getResourceId();

    Collection<XdStorageIdentifiableObject> readAsData(final XdStorageTransaction transaction) throws XdStorageException;

    boolean hasObject(final Object objectId, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    Collection<Object> readReferences(final XdStorageTransaction transaction) throws XdStorageException;

    void insertReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void deleteReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException;

    <T> T find(final Object object, final XdStorageTransaction transaction) throws XdStorageException;

    <T> Collection<T> read(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException;

    <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException;

    void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

}
