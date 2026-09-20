package org.flib.xdstorage.sqlstorage.fkresource.reference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceCache;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFkResource;
import org.flib.xdstorage.sqlstorage.fkresource.reference.controllers.XdStorageCrossDatasourceReferenceResourceFirstPhaseCommitController;
import org.flib.xdstorage.sqlstorage.fkresource.reference.controllers.XdStorageCrossDatasourceReferenceResourceRollbackForFirstPhaseCommitController;
import org.flib.xdstorage.sqlstorage.fkresource.reference.controllers.XdStorageCrossDatasourceReferenceResourceSecondPhaseCommitController;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.resource.controllers.XdStorageResourcePrepareController;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageSQLCrossDatasourceReferenceResource implements IXdStorageResourceObject<IXdStorageCrossDatasourceReferenceDaoResource>, IXdStorageCrossDatasourceReferenceDaoResource {

    private static final Logger log = LogManager.getLogger(XdStorageSQLCrossDatasourceFkResource.class);

    protected final XdStorageServicesLocator services;

    /**
     * Datasource of this resource should be a datasource of child object
     */
    private final XdStorageSQLResourceId resourceId;

    private final XdStorageClassInfo parentClassInfo;

    private final XdStorageClassInfo childClassInfo;

    private final AtomicBoolean preparedTables = new AtomicBoolean(false);

    private final Lock lockPreparingTables = new ReentrantLock();

    private final XdStorageSQLCrossDatasourceCache cache = new XdStorageSQLCrossDatasourceCache();

    public XdStorageSQLCrossDatasourceReferenceResource(final XdStorageServicesLocator services, final XdStorageSQLResourceId resourceId,
                                                        final XdStorageClassInfo parentClassInfo, final XdStorageClassInfo childClassInfo) {
        this.services = services;
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
        return childClassInfo.getClazz();
    }

    @Override
    public long getObjectsCount() {
        throw new UnsupportedOperationException("this method is not supported for references resource");
    }

    @Override
    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
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

            final XdStorageResourcePrepareController controller =
                    new XdStorageResourcePrepareController(childClassInfo, services, resourceId, manager, Collections.emptyMap());

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

    @Override
    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(services.getResourcesManager());
        final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

        final XdStorageCrossDatasourceReferenceResourceRollbackForFirstPhaseCommitController controller =
                new XdStorageCrossDatasourceReferenceResourceRollbackForFirstPhaseCommitController(resourceId, services, namingService, helper, parentClassInfo, childClassInfo);

        controller.initQueries();

        controller.markResourceAsRolledBack(services.getSqlProcessor(), transaction);
        controller.rollbackObjects(services.getSqlProcessor(), transaction);

        cache.rollbackFailedCommit(transaction);
    }

    @Override
    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.prepareCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(services.getResourcesManager());
        final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

        final XdStorageCrossDatasourceReferenceResourceFirstPhaseCommitController controller =
                new XdStorageCrossDatasourceReferenceResourceFirstPhaseCommitController(resourceId, services, namingService, helper, parentClassInfo, childClassInfo, record);

        controller.initQueries();

        controller.registerResource(services.getSqlProcessor(), transaction);
        controller.storeReferences(services.getSqlProcessor(), transaction);
    }

    @Override
    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageConnectionException, XdStorageException {
        cache.performCommit(transaction, record);

        final XdStorageSQLResourceId resourceId = cast(this.resourceId);
        final XdStorageSQLResourcesManager manager = cast(services.getResourcesManager());
        final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

        final XdStorageCrossDatasourceReferenceResourceSecondPhaseCommitController controller =
                new XdStorageCrossDatasourceReferenceResourceSecondPhaseCommitController(resourceId, services, namingService, helper, parentClassInfo, childClassInfo);

        controller.initQueries();

        controller.markResourceAsFinished(services.getSqlProcessor(), transaction);
        controller.unlockObjects(services.getSqlProcessor(), transaction);

        cache.commit(transaction);
    }

    @Override
    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        cache.rollback(transaction);
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
        services.getResourcesManager().releaseResource(this);
    }

    @Override
    public IXdStorageCrossDatasourceReferenceDaoResource getDao() {
        return this;
    }

    @Override
    public void insert(final XdStorageSQLCrossDatasourceFk fk, final XdStorageTransaction transaction) {
        cache.insert(fk, transaction);
    }

    @Override
    public void delete(final XdStorageSQLCrossDatasourceFk fk, final XdStorageTransaction transaction) throws XdStorageException {
        cache.delete(fk, transaction);
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
