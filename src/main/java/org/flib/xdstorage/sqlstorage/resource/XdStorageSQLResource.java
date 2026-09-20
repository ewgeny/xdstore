package org.flib.xdstorage.sqlstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.resource.controllers.*;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.resource.XdStorageAbstractResource;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectFieldInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageSQLResource extends XdStorageAbstractResource {

    private static final Logger log = LogManager.getLogger(XdStorageSQLResource.class);

    private final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields = new ConcurrentHashMap<>();

    private final AtomicBoolean preparedTables = new AtomicBoolean(false);

    private final Lock lockPreparingTables = new ReentrantLock();

    private final AtomicBoolean preparedLoading = new AtomicBoolean(false);

    private final AtomicBoolean preparedLoadingFully = new AtomicBoolean(false);

    private final Lock lockPreparingLoading = new ReentrantLock();

    protected XdStorageSQLResource(final XdStorageServicesLocator services,
                                   final XdStorageAbstractResourcesManager manager,
                                   final Object resourceId, final XdStorageClassInfo clInfo) {
        super(services, manager, resourceId, clInfo);
    }

    protected XdStorageSQLResource(final XdStorageServicesLocator services,
                                   final XdStorageAbstractResourcesManager manager,
                                   final Object resourceId,
                                   final XdStorageClassInfo clInfo, final boolean isReferences) {
        super(services, manager, resourceId, clInfo, isReferences);
    }

    @Override
    public void lockForCommit(XdStorageTransaction transaction) {
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
            final XdStorageSQLResourcesManager manager = cast(super.manager);
            final IXdStorageSQLTypesHelper helper = manager.typesHelper;

            collectInternalObjectsFields(clInfo, helper);

            final XdStorageResourcePrepareController controller =
                    new XdStorageResourcePrepareController(clInfo, services, resourceId, manager, internalObjectsFields);

            controller.initQueries();

            final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
            final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
            final DataSource dataSource = manager.getDataSource(config);

            Connection connection;

            try {
                connection = dataSource.getConnection();
            } catch (final SQLException e) {
                throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
            }

            try {
                connection.setAutoCommit(false);

                controller.createTables(connection, transaction);

                controller.registerForeignKeysConstrains(services.getSqlProcessor(), transaction);

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

                try {
                    connection.rollback();
                    throw toThrow;
                } catch (SQLException e1) {
                    log.error("cannot rollback transaction", e1);
                    throw new XdStorageException(e1);
                }
            } finally {
                closeConnection(connection);
            }

            preparedTables.set(true);
        } finally {
            lockPreparingTables.unlock();
        }
    }

    private void collectInternalObjectsFields(final XdStorageClassInfo clInfo, final IXdStorageSQLTypesHelper helper) {
        for (final XdStorageObjectField field : clInfo.getFields().values()) {
            if (helper.isSimpleType(field)) {
                continue;
            }

            final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

            final Class<?> keyClass = fieldInfo.getMapKeyClass();
            final Class<?> propertyClass = fieldInfo.getValueClass();

            final Class<?>[] classes;
            if (keyClass != null) {
                classes = new Class<?>[]{keyClass, propertyClass};
            } else {
                classes = new Class<?>[]{propertyClass};
            }
            internalObjectsFields.put(field, classes);
        }
    }

    @Override
    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);

        final XdStorageSQLResourcesManager manager = cast(super.manager);
        final XdStorageResourceRollbackForFirstPhaseCommitController controller =
                new XdStorageResourceRollbackForFirstPhaseCommitController(clInfo.getClazz(), resourceId, manager, internalObjectsFields);

        controller.initNamesOfTables();
        controller.initQueries();

        controller.markResourceAsRolledBack(services.getSqlProcessor(), transaction);

        controller.rollbackObjects(services.getSqlProcessor(), transaction);

        controller.rollbackLinks(services.getSqlProcessor(), transaction);

        cache.rollbackFailedCommit(transaction, changes);
    }

    @Override
    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record)
            throws XdStorageConnectionException, XdStorageException {
        cache.prepareCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);

        final XdStorageResourceFirstPhaseCommitController controller =
                new XdStorageResourceFirstPhaseCommitController(clInfo.getClazz(), services, resourceId, cast(super.manager), internalObjectsFields, record);

        controller.collectObjectsAndLinks(transaction);

        controller.initNamesOfTables();
        controller.initQueries();

        controller.registerResource(services.getSqlProcessor(), transaction);

        controller.storeObjects(services.getSqlProcessor(), transaction);

        controller.storeInternalObjectsAndLinks(services.getSqlProcessor(), transaction);
    }

    @Override
    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.performCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);

        final XdStorageSQLResourcesManager manager = cast(super.manager);
        final XdStorageResourceSecondPhaseCommitController controller =
                new XdStorageResourceSecondPhaseCommitController(clInfo.getClazz(), resourceId, manager, internalObjectsFields, record);

        controller.collectLinks();

        controller.initNamesOfTables();
        controller.initQueries();

        controller.markResourceAsFinished(services.getSqlProcessor(), transaction);

        controller.unlockObjects(services.getSqlProcessor(), transaction);

        controller.unlockLinks(services.getSqlProcessor(), transaction);

        cache.commit(transaction);
    }

    @Override
    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        cache.rollback(transaction);
    }

    @Override
    public boolean hasObject(final Object objectId, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            return cache.hasObject(objectId);
        }

        if (preparedLoading.get()) {
            if (cache.hasObject(objectId)) {
                return true;
            }
        }

        selectObjectByIdAndFillCache(objectId, transaction);

        preparedLoading.set(true);

        return cache.hasObject(objectId);
    }

    @Override
    public <T> Collection<T> read(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            return super.read(transaction);
        }

        final Collection<T> result = new ArrayList<>();
        lockPreparingLoading.lock();
        try {
            if (preparedLoadingFully.get()) {
                return super.read(transaction);
            }

            final XdStorageSQLResourceId resourceId = cast(this.resourceId);
            final XdStorageSQLResourcesManager manager = cast(super.manager);

            final XdStorageResourceReadController controller =
                    new XdStorageResourceReadController(clInfo.getClazz(), resourceId, manager, internalObjectsFields);

            controller.initQueries();

            final XdStorageSQLResourceNamingService namingService = cast(this.manager.getNamingService());
            final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
            final DataSource dataSource = manager.getDataSource(config);

            final List<T> objects = new ArrayList<>();
            Connection connection = null;

            try {
                connection = dataSource.getConnection();
            } catch (final SQLException e) {
                throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
            }

            try {

                controller.readObjects(connection, objects);

                controller.readLinks(connection, objects);

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

            // loading internal objects by links
            controller.loadInternalObjectsByLinks(objects, transaction);

            cache.fillAndCloneCacheOnlyIfNotExists(objects, result, transaction);

            preparedLoadingFully.set(true);
        } finally {
            lockPreparingLoading.unlock();
        }

        return result;
    }

    @Override
    public <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            return super.read(transaction, predicate);
        }

        lockPreparingLoading.lock();
        try {
            if (preparedLoadingFully.get()) {
                return super.read(transaction, predicate);
            }

            selectObjectsAndFillCache(transaction);

            preparedLoadingFully.set(true);
        } finally {
            lockPreparingLoading.unlock();
        }

        return super.read(transaction, predicate);
    }

    @Override
    public <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            super.watch(transaction, watcher);
            return;
        }

        lockPreparingLoading.lock();
        try {
            if (preparedLoadingFully.get()) {
                super.watch(transaction, watcher);
                return;
            }

            selectObjectsAndFillCache(transaction);

            preparedLoadingFully.set(true);
        } finally {
            lockPreparingLoading.unlock();
        }

        super.watch(transaction, watcher);
    }

    private <T> void selectObjectsAndFillCache(XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(super.manager);

        final XdStorageResourceReadController controller =
                new XdStorageResourceReadController(clInfo.getClazz(), resourceId, manager, internalObjectsFields);

        controller.initQueries();

        final XdStorageSQLResourceNamingService namingService = cast(this.manager.getNamingService());
        final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
        final DataSource dataSource = manager.getDataSource(config);

        final List<T> objects = new ArrayList<>();
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
        } catch (final SQLException e) {
            throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
        }

        try {

            controller.readObjects(connection, objects);

            controller.readLinks(connection, objects);

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

        // loading internal objects by links
        controller.loadInternalObjectsByLinks(objects, transaction);

        cache.fillCacheOnlyIfNotExists(objects);
    }

    @Override
    public void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            cache.readByReference(reference, transaction);
            return;
        }

        if (preparedLoading.get()) {
            final Object id = idField.get(reference);
            if(cache.hasObject(id)) {
                cache.readByReference(reference, transaction);
                return;
            }
        }

        selectObjectByIdAndFillCache(idField.get(reference), transaction);

        preparedLoading.set(true);

        cache.readByReference(reference, transaction);
    }

    @Override
    public Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            return cache.read(id, transaction);
        }

        if (preparedLoading.get()) {
            final Object object = cache.read(id, transaction);
            if (object != null) {
                return object;
            }
        }

        selectObjectByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        return cache.read(id, transaction);
    }

    private void selectObjectByIdAndFillCache(final Object id, XdStorageTransaction transaction)
            throws XdStorageConnectionException, XdStorageException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(super.manager);

        final XdStorageResourceReadController controller =
                new XdStorageResourceReadController(clInfo.getClazz(), resourceId, manager, internalObjectsFields);

        controller.initQueries();

        final XdStorageSQLResourceNamingService namingService = cast(this.manager.getNamingService());
        final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
        final DataSource dataSource = manager.getDataSource(config);

        final List<?> objects = new ArrayList<>();
        Connection connection = null;

        try {
            connection = dataSource.getConnection();
        } catch (final SQLException e) {
            throw new XdStorageConnectionException("cannot connect to datasource " + config.getServer(), e);
        }

        try {

            controller.readObjectById(connection, id, objects);

            controller.readLinks(connection, objects);

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

        // loading internal objects by links
        controller.loadInternalObjectsByLinks(objects, transaction);

        cache.fillCacheOnlyIfNotExists(objects);
    }

    @Override
    public void insertReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        insert(reference, transaction);
    }

    @Override
    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object id = idField.get(object);
        if (id == null && idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR) {
            final IXdStorageIdObservableWrapper wrapper = XdStorageObserverService.getObservableWrapper(object);
            cache.insert(wrapper, transaction);
            return;
        }

        if (preparedLoadingFully.get()) {
            cache.insert(object, transaction);
            return;
        }

        if (preparedLoading.get() && cache.hasObject(id)) {
            cache.insert(object, transaction);
            return;
        }

        if (id != null) {
            selectObjectByIdAndFillCache(id, transaction);
        }

        preparedLoading.set(true);

        cache.insert(object, transaction);
    }

    @Override
    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            cache.update(object, transaction);
            return;
        }

        final Object id = idField.get(object);
        if (preparedLoading.get() && cache.hasObject(id)) {
            cache.update(object, transaction);
            return;
        }

        selectObjectByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        cache.update(object, transaction);
    }

    @Override
    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object id = idField.get(object);

        if (preparedLoadingFully.get()) {
            cache.delete(id, transaction);
            return;
        }

        if (preparedLoading.get() && cache.hasObject(id)) {
            cache.delete(id, transaction);
            return;
        }

        selectObjectByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        cache.delete(id, transaction);
    }

    protected <T> T cast(final Object object) throws XdStorageException {
        try {
            return (T) object;
        } catch (final Throwable e) {
            throw new XdStorageException("cannot cast object");
        }
    }

    void closeConnection(final Connection connection) {
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
}
