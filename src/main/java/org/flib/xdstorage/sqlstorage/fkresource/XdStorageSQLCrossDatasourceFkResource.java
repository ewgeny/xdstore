package org.flib.xdstorage.sqlstorage.fkresource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.fkresource.controllers.XdStorageCrossDatasourceFkResourceRollbackForFirstPhaseCommitController;
import org.flib.xdstorage.sqlstorage.fkresource.controllers.XdStorageCrossDatasourceFkResourceFirstPhaseCommitController;
import org.flib.xdstorage.sqlstorage.fkresource.controllers.XdStorageCrossDatasourceFkResourcePrepareController;
import org.flib.xdstorage.sqlstorage.fkresource.controllers.XdStorageCrossDatasourceFkResourceSecondPhaseCommitController;
import org.flib.xdstorage.sqlstorage.fkresource.reference.XdStorageSQLCrossDatasourceReferenceResource;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageSQLCrossDatasourceFkResource implements IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource>, IXdStorageCrossDatasourceFkDaoResource {

    private static final Logger log = LogManager.getLogger(XdStorageSQLCrossDatasourceFkResource.class);

    protected final XdStorageServicesLocator services;

    protected final XdStorageAbstractResourcesManager manager;

    /**
     * Datasource of this resource should be a datasource of child object
     */
    private final XdStorageSQLResourceId resourceId;

    private final XdStorageClassInfo parentClassInfo;

    private final XdStorageClassInfo childClassInfo;

    private final AtomicBoolean preparedTables = new AtomicBoolean(false);

    private final Lock lockPreparingTables = new ReentrantLock();

    private final XdStorageSQLCrossDatasourceCache cache = new XdStorageSQLCrossDatasourceCache();

    private final Map<XdStorageSQLResourceId, XdStorageSQLCrossDatasourceReferenceResource> referenceResources = new ConcurrentHashMap<>();

    public XdStorageSQLCrossDatasourceFkResource(final XdStorageServicesLocator services, final XdStorageAbstractResourcesManager manager,
                                                 final XdStorageSQLResourceId resourceId, final XdStorageClassInfo parentClassInfo, final XdStorageClassInfo childClassInfo) {
        this.services = services;
        this.manager = manager;
        this.resourceId = resourceId;
        this.parentClassInfo = parentClassInfo;
        this.childClassInfo = childClassInfo;
    }

    @Override
    public Object getResourceId() {
        return resourceId;
    }

    @Override
    public Class<?> getObjectsClass() {
        return XdStorageSQLCrossDatasourceFk.class;
    }

    @Override
    public long getObjectsCount() {
        throw new UnsupportedOperationException("this method is not supported for foreign key resource");
    }

    @Override
    public boolean hasChanges(final XdStorageTransaction transaction) {
        return cache.hasChanges(transaction);
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
            final XdStorageSQLResourcesManager manager = cast(services.getResourcesManager());
            final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
            final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

            final XdStorageCrossDatasourceFkResourcePrepareController controller =
                    new XdStorageCrossDatasourceFkResourcePrepareController(resourceId, namingService, helper, parentClassInfo, childClassInfo);

            controller.initQueries();

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

                controller.registerForeignKeyConstraints(services.getSqlProcessor(), transaction);

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

    @Override
    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(services.getResourcesManager());
        final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

        final XdStorageCrossDatasourceFkResourceRollbackForFirstPhaseCommitController controller =
                new XdStorageCrossDatasourceFkResourceRollbackForFirstPhaseCommitController(resourceId, services, namingService, helper, parentClassInfo, childClassInfo);

        controller.initQueries();

        controller.markResourceAsRolledBack(services.getSqlProcessor(), transaction);
        controller.rollbackObjects(services.getSqlProcessor(), transaction);

        cache.rollbackFailedCommit(transaction);

        final ArrayList<XdStorageSQLCrossDatasourceReferenceResource> resources = new ArrayList<>(referenceResources.values());
        for (final XdStorageSQLCrossDatasourceReferenceResource resource : resources) {
            if (resource.hasChanges(transaction)) {
                resource.rollbackPerformingFirstPhaseCommit(transaction, new ArrayList<>());
            }
        }
    }

    @Override
    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        final ArrayList<XdStorageSQLCrossDatasourceReferenceResource> resources = new ArrayList<>(referenceResources.values());
        for (final XdStorageSQLCrossDatasourceReferenceResource resource : resources) {
            if (resource.hasChanges(transaction)) {
                resource.prepare(transaction);
            }
        }

        cache.prepareCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(services.getResourcesManager());
        final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

        final XdStorageCrossDatasourceFkResourceFirstPhaseCommitController controller =
                new XdStorageCrossDatasourceFkResourceFirstPhaseCommitController(resourceId, services, namingService, helper, parentClassInfo, childClassInfo, record);

        controller.initQueries();

        controller.registerResource(services.getSqlProcessor(), transaction);
        controller.storeForeignKeys(services.getSqlProcessor(), transaction);

        for (final XdStorageSQLCrossDatasourceReferenceResource resource : resources) {
            if (resource.hasChanges(transaction)) {
                resource.performFirstPhaseCommit(transaction, new XdStorageTransactionResourceChanges(resource));
            }
        }
    }

    @Override
    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.performCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(services.getResourcesManager());
        final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

        final XdStorageCrossDatasourceFkResourceSecondPhaseCommitController controller =
                new XdStorageCrossDatasourceFkResourceSecondPhaseCommitController(resourceId, services, namingService, helper, parentClassInfo, childClassInfo);

        controller.initQueries();

        controller.markResourceAsFinished(services.getSqlProcessor(), transaction);
        controller.unlockObjects(services.getSqlProcessor(), transaction);

        cache.commit(transaction);

        final ArrayList<XdStorageSQLCrossDatasourceReferenceResource> resources = new ArrayList<>(referenceResources.values());
        for (final XdStorageSQLCrossDatasourceReferenceResource resource : resources) {
            if (resource.hasChanges(transaction)) {
                resource.performSecondPhaseCommit(transaction, record);
            }
        }
    }

    @Override
    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        cache.rollback(transaction);

        final ArrayList<XdStorageSQLCrossDatasourceReferenceResource> resources = new ArrayList<>(referenceResources.values());
        for (final XdStorageSQLCrossDatasourceReferenceResource resource : resources) {
            if (resource.hasChanges(transaction)) {
                resource.rollback(transaction);
            }
        }
    }

    @Override
    public void lockForCommit(final XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public void unlockAfterCommit(final XdStorageTransaction transaction) {
        // do nothing
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        cache.clear(transaction);
        manager.releaseResource(this);
    }

    @Override
    public IXdStorageCrossDatasourceFkDaoResource getDao() {
        return this;
    }

    @Override
    public void insert(final XdStorageSQLResourceId parentResourceId, final Object parentObject, final XdStorageSQLResourceId childResourceId, final Object childObject, final XdStorageTransaction transaction) {
        final String parentObjectDatasource = parentResourceId.getDataSource();
        final String childObjectDatasource = childResourceId.getDataSource();
        if (!childObjectDatasource.equals(parentObjectDatasource)) {
            final XdStorageSQLCrossDatasourceFk foreignKey = new XdStorageSQLCrossDatasourceFk(parentResourceId, parentObject, childResourceId, childObject);
            cache.insert(foreignKey, transaction);

            final XdStorageSQLResourceId referenceResourceId = new XdStorageSQLResourceId();
            referenceResourceId.setTable(childResourceId.getTable());
            referenceResourceId.setDataSource(parentResourceId.getDataSource());

            final XdStorageSQLCrossDatasourceReferenceResource resource = referenceResources.computeIfAbsent(referenceResourceId, key ->
                    new XdStorageSQLCrossDatasourceReferenceResource(services, referenceResourceId, parentClassInfo, childClassInfo)
            );
            resource.insert(foreignKey, transaction);
        }
    }

    @Override
    public void delete(final XdStorageSQLResourceId parentResourceId, final Object parentObject, final XdStorageSQLResourceId childResourceId, final Object childObject, final XdStorageTransaction transaction) throws XdStorageException {
        final String parentObjectDatasource = parentResourceId.getDataSource();
        final String childObjectDatasource = childResourceId.getDataSource();
        if (!childObjectDatasource.equals(parentObjectDatasource)) {
            final XdStorageSQLCrossDatasourceFk foreignKey = new XdStorageSQLCrossDatasourceFk(parentResourceId, parentObject, childResourceId, childObject);
            cache.delete(foreignKey, transaction);

            final XdStorageSQLResourceId referenceResourceId = new XdStorageSQLResourceId();
            referenceResourceId.setTable(childResourceId.getTable());
            referenceResourceId.setDataSource(parentResourceId.getDataSource());

            final XdStorageSQLCrossDatasourceReferenceResource resource = referenceResources.computeIfAbsent(referenceResourceId, key ->
                    new XdStorageSQLCrossDatasourceReferenceResource(services, referenceResourceId, parentClassInfo, childClassInfo)
            );
            resource.delete(foreignKey, transaction);
        }
    }

    protected <T> T cast(final Object object) throws XdStorageException {
        try {
            return (T) object;
        } catch (final Throwable e) {
            throw new XdStorageException("cannot cast object");
        }
    }

    private void closeConnection(final Connection connection) {
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
