package org.flib.xdstorage;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.query.IXdStorageCriterion;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.trigger.IXdStorageTrigger;

import java.util.Collection;

public interface IXdStorage {

    String getName();

    <T> void registerTrigger(IXdStorageTrigger<T> trigger);

    IXdStorageTransaction beginTransaction(long timeout);

    IXdStorageTransaction beginTransaction();

    IXdStorageTransaction beginTransaction(IXdStorageTransaction transaction, long timeout);

    IXdStorageTransaction beginTransaction(IXdStorageTransaction transaction);

    void commitTransaction(IXdStorageTransaction transaction) throws XdStorageException;

    void rollbackTransaction(IXdStorageTransaction transaction);

    @SuppressWarnings("unchecked")
    void save(Object object) throws XdStorageException, XdStorageConnectionException;

    void save(Object object, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void save(Collection<?> objects) throws XdStorageException, XdStorageConnectionException;

    void save(Collection<?> objects, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    boolean has(Class<?> cl, Object id) throws XdStorageException, XdStorageConnectionException;

    boolean has(Class<?> cl, Object id, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void load(Object reference) throws XdStorageException, XdStorageConnectionException;

    void load(Object reference, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> T load(Class<T> cl, Object id) throws XdStorageException, XdStorageConnectionException;

    @SuppressWarnings("unchecked")
    <T> T load(Class<T> cl, Object id, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void load(Collection<?> references) throws XdStorageException, XdStorageConnectionException;

    void load(Collection<?> references, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    <T> Collection<T> load(Class<T> cl) throws XdStorageException, XdStorageConnectionException;

    @SuppressWarnings("unchecked")
    <T> Collection<T> load(Class<T> cl, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void update(Object object) throws XdStorageException, XdStorageConnectionException;

    void update(Object object, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void update(Collection<?> objects) throws XdStorageException, XdStorageConnectionException;

    void update(Collection<?> objects, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(Object reference) throws XdStorageException, XdStorageConnectionException;

    void delete(Object reference, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void delete(Collection<?> references) throws XdStorageException, XdStorageConnectionException;

    void delete(Collection<?> references, IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException;

    void shutdown();
}
