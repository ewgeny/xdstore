package io.github.xdstorage.resource;

import io.github.xdstorage.index.IXdStorageIndexResourceObject;
import io.github.xdstorage.index.XdStorageIndexType;
import io.github.xdstorage.index.btree.XdStorageBTreeIndexResource;
import io.github.xdstorage.index.hash.XdStorageHashIndexResource;
import io.github.xdstorage.search.IXdStorageSearchIndexResourceObject;
import io.github.xdstorage.search.XdStorageSearchIndexResource;
import io.github.xdstorage.services.XdStorageServicesLocator;
import io.github.xdstorage.utils.XdStorageClassInfo;

public class XdStorageResourcesManager
        extends XdStorageAbstractResourcesManager {

    protected final int fragmentSize;

    public XdStorageResourcesManager(final XdStorageServicesLocator services, final int fragmentSize) {
        super(services);

        this.fragmentSize = fragmentSize;
    }

    @Override
    protected IXdStorageIndexResourceObject createIndexResource(final Object resourceId, final String indexName,
                                                                final XdStorageClassInfo clInfo) {
        if (clInfo.getIndexType() == XdStorageIndexType.Hash) {
            return new XdStorageHashIndexResource(services, this, resourceId, indexName, clInfo, services.getIoFactory(), services.getIdGenerator(), fragmentSize);
        } else {
            return new XdStorageBTreeIndexResource(services, this, resourceId, indexName, clInfo, fragmentSize);
        }
    }

    @Override
    protected IXdStorageResourceObject<IXdStorageDaoResource> createResource(final Object resourceId,
                                                                             final XdStorageClassInfo clInfo) {
        return new XdStorageResource(services, this, resourceId, clInfo, services.getIoFactory());
    }

    @Override
    protected IXdStorageResourceObject<IXdStorageDaoResource> createReferencesResource(final Object resourceId,
                                                                                       final XdStorageClassInfo clInfo) {
        return new XdStorageResource(services, this, resourceId, clInfo, services.getIdGenerator(), true, services.getIoFactory());
    }

    @Override
    protected IXdStorageSearchIndexResourceObject createSearchIndexResource(final Object resourceId, final String indexName,
                                                                            final XdStorageClassInfo clInfo) {
        return new XdStorageSearchIndexResource(services, this, indexName, resourceId, clInfo, services.getIoFactory(), fragmentSize);
    }

}
