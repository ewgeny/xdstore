package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.XdStorageIntegerIdCounterRecord;
import org.flib.xdstorage.idgeneration.XdStorageLongIdCounterRecord;
import org.flib.xdstorage.resource.IXdStorageResourceNamingService;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.*;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

/**
 * Финальный декомпозированный сервис именования ресурсов (Поинт В).
 * Полностью избавлен от логики шардирования и конкатенации строк.
 * Делегирует роутинг в XdStorageSQLDataSourceRouter, а форматирование в XdStorageSQLTableFormatter.
 */
public class XdStorageSQLResourceNamingService implements IXdStorageResourceNamingService {

    private final XdStorageServicesLocator services;
    private final XdStorageSQLDataSourceRouter dataSourceRouter;

    public XdStorageSQLResourceNamingService(final XdStorageServicesLocator provider) {
        this.services = provider;
        this.dataSourceRouter = new XdStorageSQLDataSourceRouter(provider);
    }

    public IXdStorageSQLDataSourceConfiguration getDataSourceConfig(final String dataSourceName) {
        return dataSourceRouter.getConfiguration().getDataSourceConfig(dataSourceName);
    }

    public <T> Object getChildrenResourceId(final XdStoragePolicy policy, final Object parentObjectResourceId,
                                            final Class<?> cl, final T object, final Object objectId) throws XdStorageException {
        final XdStorageSQLClassConfiguration classCfg = dataSourceRouter.getConfiguration().getClassConfig(cl);
        if (classCfg == null) {
            throw new XdStorageException(String.format("Configuration for %s was not found", cl));
        }

        if (classCfg.isParentDataSource()) {
            final XdStorageSQLResourceId parentResourceId = (XdStorageSQLResourceId) parentObjectResourceId;
            final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
            resourceId.setTable(classCfg.getTable());
            resourceId.setDataSource(parentResourceId.getDataSource());
            return resourceId;
        }
        return getResourceId(policy, cl, object, objectId);
    }

    @Override
    public <T> Object getResourceId(final XdStoragePolicy policy, final Class<?> cl, final T object, final Object objectId) throws XdStorageException {
        return dataSourceRouter.routeResourceId(cl, object);
    }

    public Object getCrossDatasourceFkResourceId(final XdStorageSQLResourceId parentObjectResourceId, final XdStorageClassInfo parentObjectClassInfo,
                                                 final XdStorageSQLResourceId childObjectResourceId, final XdStorageClassInfo childObjectClassInfo) throws XdStorageException {
        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        resourceId.setTable(getCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class));
        resourceId.setDataSource(childObjectResourceId.getDataSource());
        return resourceId;
    }

    private String getRawTableName(final Class<?> cl) {
        if (cl == XdStorageTransaction.class) {
            return "transaction";
        }
        final XdStorageSQLClassConfiguration config = dataSourceRouter.getConfiguration().getClassConfig(cl);
        return config != null ? config.getTable() : cl.getSimpleName().toLowerCase();
    }

    public String getObjectTable(final Class<?> cl) {
        return XdStorageSQLTableFormatter.formatObjectTable(getRawTableName(cl));
    }

    public String getPrevStateObjectTable(final Class<?> cl) {
        return XdStorageSQLTableFormatter.formatPrevStateObjectTable(getRawTableName(cl));
    }

    public String getCrossDatasourceFkTable(final Class<?> parentObjectClass, final Class<?> childObjectClass, final Class<?> fkClass) throws XdStorageException {
        final XdStorageSQLClassConfiguration parentObjectConfig = dataSourceRouter.getConfiguration().getClassConfig(parentObjectClass);
        final XdStorageSQLClassConfiguration childObjectConfig = dataSourceRouter.getConfiguration().getClassConfig(childObjectClass);
        final XdStorageSQLClassConfiguration fkConfig = dataSourceRouter.getConfiguration().getClassConfig(fkClass);
        if (fkConfig == null) {
            throw new XdStorageException(String.format("Configuration for %s was not found", XdStorageSQLCrossDatasourceFk.class));
        }
        return XdStorageSQLTableFormatter.formatCrossDatasourceFkTable(fkConfig.getTable(), parentObjectConfig.getTable(), childObjectConfig.getTable());
    }

    public String getPrevStateCrossDatasourceFkTable(final Class<?> parentObjectClass, final Class<?> childObjectClass, final Class<?> fkClass) throws XdStorageException {
        final XdStorageSQLClassConfiguration parentObjectConfig = dataSourceRouter.getConfiguration().getClassConfig(parentObjectClass);
        final XdStorageSQLClassConfiguration childObjectConfig = dataSourceRouter.getConfiguration().getClassConfig(childObjectClass);
        final XdStorageSQLClassConfiguration fkConfig = dataSourceRouter.getConfiguration().getClassConfig(fkClass);
        if (fkConfig == null) {
            throw new XdStorageException(String.format("Configuration for %s was not found", XdStorageSQLCrossDatasourceFk.class));
        }
        return XdStorageSQLTableFormatter.formatPrevStateCrossDatasourceFkTable(fkConfig.getTable(), parentObjectConfig.getTable(), childObjectConfig.getTable());
    }

    public String getObjectLinksTable(final Class<?> cl, final String fieldName) {
        return XdStorageSQLTableFormatter.formatLinksTable(getRawTableName(cl), fieldName);
    }

    public String getObjectPrevStateLinksTable(final Class<?> cl, final String fieldName) {
        return XdStorageSQLTableFormatter.formatPrevStateLinksTable(getRawTableName(cl), fieldName);
    }

    public String getInternalObjectsTable(final Class<?> cl, final String fieldName) {
        return XdStorageSQLTableFormatter.formatLinksTable(getRawTableName(cl), fieldName);
    }

    public String getInternalObjectsPrevStateTable(final Class<?> cl, final String fieldName) {
        return XdStorageSQLTableFormatter.formatPrevStateLinksTable(getRawTableName(cl), fieldName);
    }

    @Override
    public Object getIndexResourceId(final String indexName, final XdStoragePolicy policy, final Class<?> cl) {
        final IXdStorageSQLDataSourceConfiguration dataSourceCfg = dataSourceRouter.getConfiguration().getCentralDataSourceConfig();

        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        resourceId.setTable(getIndexTable(cl, indexName));
        resourceId.setDataSource(dataSourceCfg.getName());
        return resourceId;
    }

    public String getIndexTable(final Class<?> cl, final String indexName) {
        return XdStorageSQLTableFormatter.formatIndexTable(getRawTableName(cl), indexName);
    }

    public String getPrevStateIndexTable(final Class<?> cl, final String indexName) {
        return XdStorageSQLTableFormatter.formatPrevStateIndexTable(getRawTableName(cl), indexName);
    }

    public String getSearchIndexTable(final Class<?> cl, final String indexName) {
        return XdStorageSQLTableFormatter.formatSearchIndexTable(getRawTableName(cl), indexName);
    }

    public String getPrevStateSearchIndexTable(final Class<?> cl, final String indexName) {
        return XdStorageSQLTableFormatter.formatPrevStateSearchIndexTable(getRawTableName(cl), indexName);
    }

    @Override
    public Object getStructureResourceId(final Class<?> cl) {
        if (cl == XdStorageLongIdCounterRecord.class) {
            final IXdStorageSQLDataSourceConfiguration dataSourceCfg = dataSourceRouter.getConfiguration().getCentralDataSourceConfig();

            final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
            resourceId.setTable("counter_long");
            resourceId.setDataSource(dataSourceCfg.getName());
            return resourceId;
        }
        if (cl == XdStorageIntegerIdCounterRecord.class) {
            final IXdStorageSQLDataSourceConfiguration dataSourceCfg = dataSourceRouter.getConfiguration().getCentralDataSourceConfig();

            final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
            resourceId.setTable("counter_integer");
            resourceId.setDataSource(dataSourceCfg.getName());
            return resourceId;
        }
        throw new XdStorageRuntimeException("database resources are not needed in structure");
    }
}
