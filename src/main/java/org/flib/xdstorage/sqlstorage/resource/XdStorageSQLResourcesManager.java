package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.IXdStorageIndexResourceObject;
import org.flib.xdstorage.resource.XdStorageResourceCommitOrder;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.datasource.IXdStorageSQLDataSourceProvider;
import org.flib.xdstorage.sqlstorage.fkresource.IXdStorageCrossDatasourceFkDaoResource;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFkResource;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLDummyCrossDatasourceFkDaoResource;
import org.flib.xdstorage.sqlstorage.index.XdStorageSQLHashIndexResource;
import org.flib.xdstorage.sqlstorage.search.XdStorageSQLSearchIndexResource;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.search.IXdStorageSearchIndexResourceObject;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import javax.sql.DataSource;

public class XdStorageSQLResourcesManager extends XdStorageAbstractResourcesManager {

    final IXdStorageSQLTypesHelper typesHelper;

    private final IXdStorageSQLDataSourceProvider dataSourceProvider;

    public XdStorageSQLResourcesManager(final XdStorageServicesLocator services) {
        super(services);

        this.typesHelper = services.getTypesHelper();
        this.dataSourceProvider = services.getDataSourceProvider();
    }

    public DataSource getDataSource(final IXdStorageSQLDataSourceConfiguration configuration) {
        return dataSourceProvider.newIfNotExistAndGet(configuration);
    }

    public IXdStorageSQLTypesHelper getTypesHelper() {
        return typesHelper;
    }

    @Override
    protected IXdStorageIndexResourceObject createIndexResource(final Object resourceId, final String indexName,
                                                                final XdStorageClassInfo clInfo) {
        return new XdStorageSQLHashIndexResource(services, this, resourceId, indexName, clInfo, services.getIdGenerator(), 100);
    }

    public IXdStorageDaoResource lockClassResource(final Object object, final XdStorageClassInfo clInfo,
                                                                final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects || policy == XdStoragePolicy.StoreAsSingleObject) {
            final Object resourceId = getNamingService().getResourceId(policy, clInfo.getClazz(), object, null);
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createResource(resourceId, clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.DATA_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource != null ? resource.getDao() : null;
    }

    @Override
    protected IXdStorageResourceObject<IXdStorageDaoResource> createResource(final Object resourceId, final XdStorageClassInfo clInfo) {
        return new XdStorageSQLResource(services, this, resourceId, clInfo);
    }

    @Override
    protected IXdStorageResourceObject<IXdStorageDaoResource> createReferencesResource(final Object resourceId, final XdStorageClassInfo clInfo) {
        return new XdStorageSQLResource(services,this, resourceId, clInfo, true);
    }

    @Override
    protected IXdStorageSearchIndexResourceObject createSearchIndexResource(final Object resourceId, final String indexName, final XdStorageClassInfo clInfo) {
        return new XdStorageSQLSearchIndexResource(services,this, resourceId, indexName, clInfo);
    }

    public IXdStorageDaoResource lockChildrenClassResource(final XdStorageClassInfo clInfo, final Object parentObjectResourceId,
                                                           final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) services.getNamingService();
            final Object resourceId = namingService.getChildrenResourceId(policy, parentObjectResourceId, clInfo.getClazz(), null, null);
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createResource(resourceId, clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.CHILDREN_DATA_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource != null ? resource.getDao() : null;
    }

    public IXdStorageCrossDatasourceFkDaoResource lockForeignKeyObjectsResource(final XdStorageSQLResourceId parentObjectResourceId, final XdStorageSQLResourceId childObjectResourceId,
                                                                                final XdStorageTransaction transaction, final XdStorageClassInfo parentObjectClassInfo,
                                                                                final XdStorageClassInfo childObjectClassInfo) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageCrossDatasourceFkDaoResource> resource = null;
        final String parentObjectDatasource = parentObjectResourceId.getDataSource();
        final String childObjectDatasource = childObjectResourceId.getDataSource();
        if (!childObjectDatasource.equals(parentObjectDatasource))
        {
            final XdStorageSQLResourceNamingService namingService = (XdStorageSQLResourceNamingService) services.getNamingService();
            final Object resourceId = namingService.getCrossDatasourceFkResourceId(parentObjectResourceId, parentObjectClassInfo, childObjectResourceId, childObjectClassInfo);
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return new XdStorageSQLCrossDatasourceFkResource(services, XdStorageSQLResourcesManager.this, (XdStorageSQLResourceId) resourceId,
                            parentObjectClassInfo, childObjectClassInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.CROSS_DATASOURCE_FK_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource != null ? resource.getDao() : XdStorageSQLDummyCrossDatasourceFkDaoResource.getInstance().getDao();
    }
}
