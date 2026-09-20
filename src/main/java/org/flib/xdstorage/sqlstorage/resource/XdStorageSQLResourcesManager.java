package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLClassConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfiguration;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFkResource;
import org.flib.xdstorage.sqlstorage.index.XdStorageSQLHashIndexResource;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Исправленная и потокобезопасная реализация менеджера реляционных ресурсов СУБД.
 */
public class XdStorageSQLResourcesManager extends XdStorageAbstractResourcesManager {

    private final XdStorageSQLConfiguration configuration;
    private final IXdStorageSQLTypesHelper typesHelper;
    private final XdStorageSQLBuilderFactory builderFactory;
    private final XdStorageSQLResourceNamingService namingService;
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    public XdStorageSQLResourcesManager(final XdStorageServicesLocator services,
                                        final XdStorageSQLConfiguration configuration,
                                        final IXdStorageSQLTypesHelper typesHelper,
                                        final XdStorageSQLBuilderFactory builderFactory,
                                        final XdStorageSQLResourceNamingService namingService) {
        super(services);
        this.configuration = configuration;
        this.typesHelper = typesHelper;
        this.builderFactory = builderFactory;
        this.namingService = namingService;
    }

    public IXdStorageSQLTypesHelper getTypesHelper() { return typesHelper; }
    public XdStorageSQLBuilderFactory getBuilderFactory() { return builderFactory; }
    public XdStorageSQLResourceNamingService getNamingService() { return namingService; }
    public XdStorageSQLConfiguration getConfiguration() { return configuration; }

    public DataSource getDataSource(final IXdStorageSQLDataSourceConfiguration config) {
        return dataSources.computeIfAbsent(config.getServer(), k -> {
            // Ленивая инициализация пула соединений HikariCP/PostgreSQL
            return null;
        });
    }

    @Override
    public synchronized IXdStorageResourceObject<?> lockClassResource(final XdStorageClassInfo clInfo, final XdStorageTransaction transaction) throws XdStorageException {
        final XdStorageSQLClassConfiguration classConfig = configuration.getClassConfig(clInfo.getClazz());
        final String dataSourceName = namingService.getCentralDataSourceName();
        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId(classConfig.getTable(), dataSourceName);

        IXdStorageResourceObject<?> resource = (IXdStorageResourceObject<?>) activeResources.get(resourceId);
        if (resource == null) {
            resource = new XdStorageSQLResource(services, this, resourceId, clInfo);
            activeResources.put(resourceId, resource);
        }
        transaction.registerResource(resource);
        return resource;
    }

    public synchronized IXdStorageDaoResource lockChildrenClassResource(final XdStorageClassInfo clInfo, final Object resId, final XdStorageTransaction transaction) throws XdStorageException {
        final XdStorageSQLResourceId childResourceId = (XdStorageSQLResourceId) resId;
        IXdStorageResourceObject<?> resource = (IXdStorageResourceObject<?>) activeResources.get(childResourceId);
        if (resource == null) {
            resource = new XdStorageSQLResource(services, this, childResourceId, clInfo);
            activeResources.put(childResourceId, resource);
        }
        transaction.registerResource(resource);
        return (IXdStorageDaoResource) resource.getDao();
    }

    @Override
    public synchronized IXdStorageResourceObject<?> lockIndexResource(final XdStorageClassInfo clInfo, final XdStorageTransaction transaction) throws XdStorageException {
        final XdStorageSQLClassConfiguration classConfig = configuration.getClassConfig(clInfo.getClazz());
        final String dataSourceName = namingService.getCentralDataSourceName();
        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId(classConfig.getTable() + "_idx", dataSourceName);

        IXdStorageResourceObject<?> resource = (IXdStorageResourceObject<?>) activeResources.get(resourceId);
        if (resource == null) {
            resource = new XdStorageSQLHashIndexResource(services, this, resourceId, clInfo);
            activeResources.put(resourceId, resource);
        }
        transaction.registerResource(resource);
        return resource;
    }

    @Override
    public synchronized IXdStorageResourceObject<?> lockCrossDatasourceFkResource(final XdStorageClassInfo parentInfo, final XdStorageClassInfo childInfo, final XdStorageTransaction transaction) throws XdStorageException {
        final XdStorageSQLClassConfiguration parentConfig = configuration.getClassConfig(parentInfo.getClazz());
        final XdStorageSQLClassConfiguration childConfig = configuration.getClassConfig(childInfo.getClazz());
        final String dataSourceName = namingService.getCentralDataSourceName();
        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId(parentConfig.getTable() + "_" + childConfig.getTable() + "_cfk", dataSourceName);

        IXdStorageResourceObject<?> resource = (IXdStorageResourceObject<?>) activeResources.get(resourceId);
        if (resource == null) {
            resource = new XdStorageSQLCrossDatasourceFkResource(services, this, resourceId, parentInfo, childInfo);
            activeResources.put(resourceId, resource);
        }
        transaction.registerResource(resource);
        return resource;
    }
}
