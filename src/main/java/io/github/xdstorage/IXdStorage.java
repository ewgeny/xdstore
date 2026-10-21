package io.github.xdstorage;

import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.search.query.IXdStorageCriterion;
import io.github.xdstorage.search.query.XdStorageSearchQuery;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.transaction.IXdStorageTransactionManager;
import io.github.xdstorage.trigger.IXdStorageTrigger;

import java.util.Collection;

public interface IXdStorage {

    String getName();

    /** O-1: operational counters for this storage (see XdStorageMetrics). */
    io.github.xdstorage.metrics.XdStorageMetrics getMetrics();

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

    IXdStorageTransactionManager getTransactionManager();

    void shutdown();
}
