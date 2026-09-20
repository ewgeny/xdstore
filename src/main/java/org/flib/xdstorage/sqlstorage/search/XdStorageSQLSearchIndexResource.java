package org.flib.xdstorage.sqlstorage.search;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.search.controllers.*;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageResourceCache;
import org.flib.xdstorage.search.IXdStorageSearchIndexDaoResource;
import org.flib.xdstorage.search.IXdStorageSearchIndexResourceObject;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageSQLSearchIndexResource implements IXdStorageSearchIndexResourceObject, IXdStorageSearchIndexDaoResource {

    private static final Logger log = LogManager.getLogger(XdStorageSQLSearchIndexResource.class);

    protected final XdStorageServicesLocator services;

    protected final XdStorageSQLResourcesManager manager;

    protected final Object resourceId;

    protected final String indexName;

    protected final XdStorageClassInfo clInfo;

    protected final XdStorageObjectIdField idField;

    protected final XdStorageObjectIdField indexIdField;

    protected final XdStorageResourceCache cache;

    private final AtomicBoolean preparedTables = new AtomicBoolean(false);

    private final Lock lockPreparingTables = new ReentrantLock();

    private final AtomicBoolean preparedLoading = new AtomicBoolean(false);

    private final AtomicBoolean preparedLoadingFully = new AtomicBoolean(false);

    private final Lock lockPreparingLoading = new ReentrantLock();

    public XdStorageSQLSearchIndexResource(final XdStorageServicesLocator services,
                                           final XdStorageAbstractResourcesManager manager,
                                           final Object resourceId, final String indexName,
                                           final XdStorageClassInfo clInfo) {
        this.services = services;
        this.manager = (XdStorageSQLResourcesManager) manager;
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.clInfo = clInfo;
        this.idField = clInfo.getIdField();
        this.indexIdField = XdStorageObjectUtils.getClassInfo(XdStorageSearchIndexRecord.class).getIdField();

        final XdStorageClassInfo sirClInfo = XdStorageObjectUtils.getClassInfo(XdStorageSearchIndexRecord.class);
        this.cache = new XdStorageResourceCache(services, sirClInfo.getClazz(), sirClInfo.getIdField());
    }

    @Override
    public Object getResourceId() {
        return resourceId;
    }

    @Override
    public Class<?> getObjectsClass() {
        return XdStorageSearchIndexRecord.class;
    }

    @Override
    public long getObjectsCount() {
        return cache.getObjectsCount();
    }

    @Override
    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
        return cache.hasChanges(transaction);
    }

    @Override
    public void lockForCommit(final XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public void unlockAfterCommit(XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public void prepare(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedTables.get()) {
            return;
        }

        lockPreparingTables.lock();
        try {
            if (preparedTables.get()) {
                return;
            }

            final XdStorageSQLResourceId resourceId = cast(this.resourceId);

            final XdStorageSearchIndexPrepareController controller =
                    new XdStorageSearchIndexPrepareController(clInfo, services, resourceId, indexName, manager);

            controller.initQueries();

            final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
            final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
            final DataSource dataSource = manager.getDataSource(config);

            Connection connection = null;

            try {
                connection = dataSource.getConnection();
            } catch (final SQLException e) {
                throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
            }

            try {
                connection.setAutoCommit(false);

                controller.createTables(connection, transaction);

                connection.commit();
            } catch (final XdStorageRuntimeException e) {
                throw new XdStorageException(e);
            } catch (final SQLException e) {
                log.warn("preparing resource error", e);
                XdStorageException toThrow = new XdStorageException(e);
                if (e.getNextException() != null) {
                    log.error("The next exception", e.getNextException());
                    toThrow = new XdStorageException(e.getNextException());
                }
                if (connection != null) {
                    try {
                        connection.rollback();
                        throw toThrow;
                    } catch (SQLException e1) {
                        log.error("cannot rollback transaction", e1);
                        throw new XdStorageException(e1);
                    }
                }
            } finally {
                closeConnection(connection);
            }

            preparedTables.set(true);
        } finally {
            lockPreparingTables.unlock();
        }
    }

    @Override
    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException {
        cache.rollbackFailedCommit(transaction, changes);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);

        final XdStorageSearchIndexRollbackForFirstPhaseCommitController controller =
                new XdStorageSearchIndexRollbackForFirstPhaseCommitController(clInfo, resourceId, indexName, manager);

        controller.initQueries();

        controller.markResourceAsRolledBack(services.getSqlProcessor(), transaction);

        controller.rollbackIndexRecords(services.getSqlProcessor(), transaction);
    }

    @Override
    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.prepareCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);

        final XdStorageSearchIndexFirstPhaseCommitController controller =
                new XdStorageSearchIndexFirstPhaseCommitController(clInfo, resourceId, indexName, manager, record);

        controller.initQueries();

        controller.registerResource(services.getSqlProcessor(), transaction);

        controller.storeIndexRecords(services.getSqlProcessor(), transaction);
    }

    @Override
    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.performCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);

        final XdStorageSearchIndexSecondPhaseCommitController controller =
                new XdStorageSearchIndexSecondPhaseCommitController(clInfo, resourceId, indexName, manager, record);

        controller.initQueries();

        controller.markResourceAsFinished(services.getSqlProcessor(), transaction);

        controller.unlockIndexRecords(services.getSqlProcessor(), transaction);

        cache.commit(transaction);
    }

    @Override
    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        cache.rollback(transaction);
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        cache.clear(transaction);
        services.getResourcesManager().releaseResource(this);
    }

    @Override
    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = indexIdField.get(object);
        if(objectId == null) {
            cache.insert(object, transaction);
        } else {
            selectIndexRecordByIdAndFillCache(objectId, transaction);

            cache.insert(object, transaction);
        }
    }

    private void selectIndexRecordByIdAndFillCache(final Object id, final XdStorageTransaction transaction) throws XdStorageConnectionException, XdStorageException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
        final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
        final DataSource dataSource = manager.getDataSource(config);

        final XdStorageSearchIndexReadController controller =
                new XdStorageSearchIndexReadController(clInfo, dataSource, resourceId, indexName, manager);

        controller.initQueries();

        final List<XdStorageSearchIndexRecord> objects = new ArrayList<>();
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
        } catch (final SQLException e) {
            throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
        }

        try {

            controller.readIndexRecordById(connection, id, objects);

        } catch (final SQLException e) {
            XdStorageException toThrow = new XdStorageException(e);
            if (e.getNextException() != null) {
                log.error("The next exception", e.getNextException());
                toThrow = new XdStorageException(e.getNextException());
            }
            throw toThrow;
        } catch (final Throwable e) {
            throw new XdStorageException(e);
        } finally {
            closeConnection(connection);
        }

        cache.fillCacheOnlyIfNotExists(objects);
    }

    @Override
    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if(preparedLoadingFully.get()) {
            cache.update(object, transaction);
            return;
        }

        final Object objectId = indexIdField.get(object);
        if(preparedLoading.get() && cache.hasObject(objectId)) {
            cache.update(object, transaction);
            return;
        }

        selectIndexRecordByIdAndFillCache(objectId, transaction);

        preparedLoading.set(true);

        cache.update(object, transaction);
    }

    @Override
    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = indexIdField.get(object);

        if(preparedLoadingFully.get()) {
            cache.delete(objectId, transaction);
            return;
        }

        if(preparedLoading.get() && cache.hasObject(objectId)) {
            cache.delete(objectId, transaction);
            return;
        }

        selectIndexRecordByIdAndFillCache(objectId, transaction);

        preparedLoading.set(true);

        cache.delete(objectId, transaction);
    }

    @Override
    public <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            cache.watch(transaction, watcher);
            return;
        }

        lockPreparingLoading.lock();
        try {
            if (preparedLoadingFully.get()) {
                cache.watch(transaction, watcher);
                return;
            }

            final XdStorageSQLResourceId resourceId = cast(this.resourceId);
            final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
            final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
            final DataSource dataSource = manager.getDataSource(config);

            final XdStorageSearchIndexReadController controller =
                    new XdStorageSearchIndexReadController(clInfo, dataSource, resourceId, indexName, manager);

            controller.initQueries();

            final List<Object> records = new ArrayList<>();
            Connection connection = null;

            try {
                connection = dataSource.getConnection();
            } catch (final SQLException e) {
                throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
            }

            try {

                controller.readIndexRecords(connection, records);

            } catch (final SQLException e) {
                XdStorageException toThrow = new XdStorageException(e);
                if (e.getNextException() != null) {
                    log.error("The next exception", e.getNextException());
                    toThrow = new XdStorageException(e.getNextException());
                }
                throw toThrow;
            } catch (final Throwable e) {
                throw new XdStorageException(e);
            } finally {
                closeConnection(connection);
            }

            cache.fillCacheOnlyIfNotExists(records);

            preparedLoadingFully.set(true);
        } finally {
            lockPreparingLoading.unlock();
        }

        cache.watch(transaction, watcher);
    }

    @Override
    public List<IXdStorageSimpleWrapper> selectObjectReferences(final XdStorageClassInfo clInfo, final XdStorageSqlSearchQuery query,
                                                                    final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
        final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
        final DataSource dataSource = manager.getDataSource(config);

        final XdStorageSearchIndexSelectController controller =
                new XdStorageSearchIndexSelectController(clInfo, indexName, manager, services);

        controller.buildQuery(query);

        Connection connection;

        try {
            connection = dataSource.getConnection();
        } catch (final SQLException e) {
            throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
        }

        try {

            return controller.selectObjectReferences(connection, transaction);

        } catch (final SQLException e) {
            XdStorageException toThrow = new XdStorageException(e);
            if (e.getNextException() != null) {
                log.error("The next exception", e.getNextException());
                toThrow = new XdStorageException(e.getNextException());
            }
            throw toThrow;
        } catch (final Throwable e) {
            throw new XdStorageException(e);
        } finally {
            closeConnection(connection);
        }
    }

    protected <T> T cast(final Object object) throws XdStorageException {
        try {
            return (T) object;
        } catch (final Throwable e) {
            throw new XdStorageException("cannot cast object");
        }
    }

    protected void closeConnection(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (final SQLException e) {
                log.error("closing connection error", e);
                if (e.getNextException() != null) {
                    log.error("The next exception", e.getNextException());
                }
            }
        }
    }

    @Override
    public IXdStorageSearchIndexDaoResource getDao() {
        return this;
    }
}
