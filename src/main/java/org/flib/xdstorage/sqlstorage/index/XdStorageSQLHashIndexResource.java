package org.flib.xdstorage.sqlstorage.index;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.index.IXdStorageChangeIndexRollback;
import org.flib.xdstorage.index.hash.XdStorageAbstractHashIndexResource;
import org.flib.xdstorage.index.hash.XdStorageHashIndexRecord;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLClassConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfiguration;
import org.flib.xdstorage.sqlstorage.fkresource.IXdStorageCrossDatasourceFkDaoResource;
import org.flib.xdstorage.sqlstorage.index.controllers.*;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectFieldInfo;
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

public class XdStorageSQLHashIndexResource extends XdStorageAbstractHashIndexResource {

    private static final Logger log = LogManager.getLogger(XdStorageSQLHashIndexResource.class);

    private final AtomicBoolean preparedTables = new AtomicBoolean(false);

    private final Lock lockPreparingTables = new ReentrantLock();

    private final AtomicBoolean preparedLoading = new AtomicBoolean(false);

    private final AtomicBoolean preparedLoadingFully = new AtomicBoolean(false);

    private final Lock lockPreparingLoading = new ReentrantLock();

    public XdStorageSQLHashIndexResource(final XdStorageServicesLocator services,
                                         final XdStorageAbstractResourcesManager manager,
                                         final Object resourceId, final String indexName, final XdStorageClassInfo clInfo,
                                         final IXdStorageIdGenerator idGenerator, final int fragmentSize) {
        super(services, manager, resourceId, indexName, clInfo, idGenerator, fragmentSize);
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

            final XdStorageIndexPrepareController controller =
                    new XdStorageIndexPrepareController(objectClInfo, services, resourceId, indexName, manager);

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
    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction,
                                                   final Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException {
        cache.rollbackFailedCommit(transaction, changes);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(super.manager);

        final XdStorageIndexRollbackForFirstPhaseCommitController controller =
                new XdStorageIndexRollbackForFirstPhaseCommitController(objectClInfo, resourceId, indexName, manager);

        controller.initQueries();

        controller.markResourceAsRolledBack(services.getSqlProcessor(), transaction);

        controller.rollbackIndexRecords(services.getSqlProcessor(), transaction);

        postRollback(transaction);
    }

    @Override
    public void performFirstPhaseCommit(final XdStorageTransaction transaction,
                                        final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.prepareCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(super.manager);
        final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
        final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());

        final XdStorageIndexFirstPhaseCommitController controller =
                new XdStorageIndexFirstPhaseCommitController(objectClInfo, resourceId, indexName, manager, record);

        controller.initQueries();

        controller.registerResource(services.getSqlProcessor(), transaction);

        controller.storeIndexRecords(services.getSqlProcessor(), transaction);
    }

    @Override
    public void performSecondPhaseCommit(final XdStorageTransaction transaction,
                                         final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.performCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);

        final XdStorageSQLResourcesManager manager = cast(super.manager);
        final XdStorageIndexSecondPhaseCommitController controller =
                new XdStorageIndexSecondPhaseCommitController(objectClInfo, resourceId, indexName, manager, record);

        controller.initQueries();

        controller.markResourceAsFinished(services.getSqlProcessor(), transaction);

        controller.unlockIndexRecords(services.getSqlProcessor(), transaction);

        cache.commit(transaction);
        postCommit(transaction);
    }

    @Override
    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        cache.rollback(transaction);
        postRollback(transaction);
    }

    @Override
    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        Object id = objectIdField.get(object);

        if(id == null) {
            final XdStorageSQLConfiguration configuration = services.getConfiguration();
            final XdStorageSQLClassConfiguration classConfig = configuration.getClassConfig(objectClInfo.getClazz());
            if (objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR) {
                final XdStorageObjectFieldInfo fieldInfo = objectIdField.getFieldInfo();
                if (classConfig.isMultiple()
                        && (fieldInfo.getValueClass() == String.class || fieldInfo.getValueClass() == Long.class || fieldInfo.getValueClass() == Integer.class)) {
                    objectIdField.set(object, id = idGenerator.generate(objectClInfo.getClazz(), services.getStorage(), transaction));
                }
            } else if (objectIdField.getIdGeneratorClass() != null) {
                objectIdField.set(object, id = idGenerator.generate(objectClInfo.getClazz(), services.getStorage(), transaction));
            }
        }

        final IXdStorageDaoResource resource = manager.lockClassResource(object, objectClInfo, transaction);
        resource.insert(object, transaction);

        final XdStorageHashIndexRecord record = new XdStorageHashIndexRecord(id, resource.getResourceId());
        if (id == null && objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR) {
            final IXdStorageIdObservableWrapper wrapper = XdStorageObserverService.getObservableWrapper(record);
            cache.insert(wrapper, transaction);

            final IXdStorageIdObservableWrapper objectWrapper =
                    resource.find(XdStorageObserverService.getObservableWrapper(object), transaction);
            objectWrapper.addObserver(new XdStorageAbstractIdObserver() {
                @Override
                public void onNewIdIsSet(final IXdStorageIdObservableWrapper objectWrapper, final Object id) {
                    idField.set(wrapper, id);
                    index.insertRecord(id, resource.getResourceId());
                    registerRollback(transaction, new IXdStorageChangeIndexRollback() {

                        @Override
                        public void rollback() {
                            index.deleteRecord(id);
                        }
                    });
                }
            });
        } else {
            selectIndexRecordByIdAndFillCache(id, transaction);

            cache.insert(record, transaction);
            index.insertRecord(id, resource.getResourceId());
            final Object objectId = id;
            registerRollback(transaction, new IXdStorageChangeIndexRollback() {

                @Override
                public void rollback() {
                    index.deleteRecord(objectId);
                }
            });
        }
    }

    public void insertChild(final Object parentObject, final Object childObject, final XdStorageSQLResourceId parentObjectResourceId,
                            final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        Object id = objectIdField.get(childObject);

        final XdStorageSQLConfiguration configuration = services.getConfiguration();
        final XdStorageSQLClassConfiguration classConfig = configuration.getClassConfig(objectClInfo.getClazz());

        if(id == null) {
            if (objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR) {
                final XdStorageObjectFieldInfo fieldInfo = objectIdField.getFieldInfo();
                if ((classConfig.isMultiple() || classConfig.isParentDataSource())
                        && (fieldInfo.getValueClass() == String.class || fieldInfo.getValueClass() == Long.class || fieldInfo.getValueClass() == Integer.class)) {
                    objectIdField.set(childObject, id = idGenerator.generate(objectClInfo.getClazz(), services.getStorage(), transaction));
                }
            } else if (objectIdField.getIdGeneratorClass() != null) {
                objectIdField.set(childObject, id = idGenerator.generate(objectClInfo.getClazz(), services.getStorage(), transaction));
            }
        }

        final IXdStorageDaoResource resource = ((XdStorageSQLResourcesManager)manager).lockChildrenClassResource(objectClInfo, parentObjectResourceId, transaction);
        resource.insert(childObject, transaction);

        final XdStorageHashIndexRecord record = new XdStorageHashIndexRecord(id, resource.getResourceId());
        if (id == null && objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR) {
            final IXdStorageIdObservableWrapper wrapper = XdStorageObserverService.getObservableWrapper(record);
            cache.insert(wrapper, transaction);

            final IXdStorageIdObservableWrapper objectWrapper =
                    resource.find(XdStorageObserverService.getObservableWrapper(childObject), transaction);
            objectWrapper.addObserver(new XdStorageAbstractIdObserver() {
                @Override
                public void onNewIdIsSet(final IXdStorageIdObservableWrapper objectWrapper, final Object id) {
                    idField.set(wrapper, id);
                    index.insertRecord(id, resource.getResourceId());
                    registerRollback(transaction, new IXdStorageChangeIndexRollback() {

                        @Override
                        public void rollback() {
                            index.deleteRecord(id);
                        }
                    });
                }
            });
        } else {
            selectIndexRecordByIdAndFillCache(id, transaction);

            cache.insert(record, transaction);
            index.insertRecord(id, resource.getResourceId());
            final Object objectId = id;
            registerRollback(transaction, new IXdStorageChangeIndexRollback() {

                @Override
                public void rollback() {
                    index.deleteRecord(objectId);
                }
            });

            // insert foreign key
            if (classConfig.isMultiple()) {
                final XdStorageClassInfo parentObjectClassInfo = XdStorageObjectUtils.getClassInfo(parentObject.getClass());
                final IXdStorageCrossDatasourceFkDaoResource fkResource = ((XdStorageSQLResourcesManager) manager)
                        .lockForeignKeyObjectsResource(parentObjectResourceId, (XdStorageSQLResourceId)resource.getResourceId(), transaction, parentObjectClassInfo, objectClInfo);
                fkResource.insert(parentObjectResourceId, parentObject, (XdStorageSQLResourceId)resource.getResourceId(), childObject, transaction);
            }
        }
    }

    private void selectIndexRecordByIdAndFillCache(final Object id, final XdStorageTransaction transaction) throws XdStorageConnectionException, XdStorageException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(super.manager);
        final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
        final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
        final DataSource dataSource = manager.getDataSource(config);

        final XdStorageIndexReadController controller =
                new XdStorageIndexReadController(objectClInfo, dataSource, resourceId, indexName, manager);

        controller.initQueries();

        final List<?> objects = new ArrayList<>();
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
        objects.stream().forEach(record -> {
            final XdStorageHashIndexRecord irec = (XdStorageHashIndexRecord) record;
            index.insertRecord(irec.getObjectId(), irec.getResourceId());
        });
    }

    @Override
    public boolean has(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            return cache.hasObject(id);
        }

        if (preparedLoading.get()) {
            if (cache.hasObject(id)) {
                return true;
            }
        }

        selectIndexRecordByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        return cache.hasObject(id);
    }

    @Override
    public Collection<Object> read(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            return super.read(transaction);
        }

        lockPreparingLoading.lock();
        try {
            if (preparedLoadingFully.get()) {
                return super.read(transaction);
            }

            final XdStorageSQLResourceId resourceId = cast(this.resourceId);
            final XdStorageSQLResourcesManager manager = cast(super.manager);
            final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
            final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
            final DataSource dataSource = manager.getDataSource(config);

            final XdStorageIndexReadController controller =
                    new XdStorageIndexReadController(objectClInfo, dataSource, resourceId, indexName, manager);

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
            records.parallelStream().forEach(record -> {
                final XdStorageHashIndexRecord irec = (XdStorageHashIndexRecord) record;
                index.insertRecord(irec.getObjectId(), irec.getResourceId());
            });

            preparedLoadingFully.set(true);
        } finally {
            lockPreparingLoading.unlock();
        }

        return super.read(transaction);
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

            final XdStorageSQLResourceId resourceId = cast(this.resourceId);
            final XdStorageSQLResourcesManager manager = cast(super.manager);
            final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
            final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
            final DataSource dataSource = manager.getDataSource(config);

            final XdStorageIndexReadController controller =
                    new XdStorageIndexReadController(objectClInfo, dataSource, resourceId, indexName, manager);

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
            records.parallelStream().forEach(record -> {
                final XdStorageHashIndexRecord irec = (XdStorageHashIndexRecord) record;
                index.insertRecord(irec.getObjectId(), irec.getResourceId());
            });

            preparedLoadingFully.set(true);
        } finally {
            lockPreparingLoading.unlock();
        }

        return super.read(transaction, predicate);
    }

    public void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object id = objectIdField.get(reference);

        if (preparedLoadingFully.get()) {
            super.readByReference(reference, transaction);
        }

        if (preparedLoading.get()) {
            if (cache.hasObject(id)) {
                super.readByReference(reference, transaction);
            }
        }

        selectIndexRecordByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        super.readByReference(reference, transaction);
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

            final XdStorageSQLResourceId resourceId = cast(this.resourceId);
            final XdStorageSQLResourcesManager manager = cast(super.manager);
            final XdStorageSQLResourceNamingService namingService = cast(manager.getNamingService());
            final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(resourceId.getDataSource());
            final DataSource dataSource = manager.getDataSource(config);

            final XdStorageIndexReadController controller =
                    new XdStorageIndexReadController(objectClInfo, dataSource, resourceId, indexName, manager);

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
            records.parallelStream().forEach(record -> {
                final XdStorageHashIndexRecord irec = (XdStorageHashIndexRecord) record;
                index.insertRecord(irec.getObjectId(), irec.getResourceId());
            });

            preparedLoadingFully.set(true);
        } finally {
            lockPreparingLoading.unlock();
        }

        super.watch(transaction, watcher);
    }

    @Override
    public Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            return super.read(id, transaction);
        }

        if (preparedLoading.get()) {
            final Object object = super.read(id, transaction);
            if (object != null) {
                return object;
            }
        }

        selectIndexRecordByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        return super.read(id, transaction);
    }

    @Override
    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            super.update(object, transaction);
            return;
        }

        final Object id = objectIdField.get(object);
        if (preparedLoading.get() && cache.hasObject(id)) {
            super.update(object, transaction);
            return;
        }

        selectIndexRecordByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        super.update(object, transaction);
    }

    @Override
    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            super.delete(object, transaction);
            return;
        }

        final Object id = objectIdField.get(object);
        if (preparedLoading.get() && cache.hasObject(id)) {
            super.delete(object, transaction);
            return;
        }

        selectIndexRecordByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        super.delete(object, transaction);
    }

    public void deleteChild(final Object parentObject, final Object childObject, final XdStorageSQLResourceId parentObjectResourceId,
                            final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (preparedLoadingFully.get()) {
            super.delete(childObject, transaction);
            return;
        }

        final Object id = objectIdField.get(childObject);
        if (preparedLoading.get() && cache.hasObject(id)) {
            super.delete(childObject, transaction);
            return;
        }

        selectIndexRecordByIdAndFillCache(id, transaction);

        preparedLoading.set(true);

        super.delete(childObject, transaction);

        deleteForeignKey(parentObject, childObject, parentObjectResourceId, transaction);
    }

    public void deleteForeignKey(final Object parentObject, final Object childObject, final XdStorageSQLResourceId parentObjectResourceId,
                                 final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        // delete foreign key
        final XdStorageSQLConfiguration configuration = services.getConfiguration();
        final XdStorageSQLClassConfiguration classConfig = configuration.getClassConfig(objectClInfo.getClazz());

        if (classConfig.isMultiple()) {
            final Object objectId = objectIdField.get(childObject);
            final XdStorageSQLResourceId resourceId = (XdStorageSQLResourceId)index.getResourceId(objectId);

            final XdStorageClassInfo parentObjectClassInfo = XdStorageObjectUtils.getClassInfo(parentObject.getClass());
            final IXdStorageCrossDatasourceFkDaoResource fkResource = ((XdStorageSQLResourcesManager) manager)
                    .lockForeignKeyObjectsResource(parentObjectResourceId, resourceId, transaction, parentObjectClassInfo, objectClInfo);
            fkResource.delete(parentObjectResourceId, parentObject, resourceId, childObject, transaction);
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
}
