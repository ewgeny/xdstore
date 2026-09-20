package org.flib.xdstorage.resource;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.index.IXdStorageIndexResourceObject;
import org.flib.xdstorage.search.IXdStorageSearchIndexDaoResource;
import org.flib.xdstorage.search.IXdStorageSearchIndexResourceObject;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class XdStorageAbstractResourcesManager {

    protected final XdStorageServicesLocator services;

    protected final Map<Object, IXdStorageResourceObject<?>> resources;

    /**
     * [resource_id, count_objects]
     */
    protected final Map<Class<?>, Map<Object, AtomicLong>> countObjects;

    private final Map<Object, AtomicLong> locks;

    public XdStorageAbstractResourcesManager(final XdStorageServicesLocator services) {
        this.services = services;
        this.resources = new ConcurrentHashMap<>();
        this.countObjects = new ConcurrentHashMap<>();
        this.locks = new ConcurrentHashMap<>();

    }

    public IXdStorageResourceNamingService getNamingService() {
        return services.getNamingService();
    }

    public IXdStorageIndexDaoResource lockIndexResource(final XdStorageClassInfo clInfo,
                                                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageIndexResourceObject resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects || policy == XdStoragePolicy.StoreWithParentObject) {
            final Object resourceId = getNamingService().getIndexResourceId("index", policy, clInfo.getClazz());
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createIndexResource(resourceId, "index", clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.INDEX_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource.getDao();
    }

    protected abstract IXdStorageIndexResourceObject createIndexResource(final Object resourceId, final String indexName,
                                                                         final XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockResource(final Object resourceId, final XdStorageClassInfo clInfo,
                                              final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageResourceObject<IXdStorageDaoResource> resource = internalLockResource(new IResourceFactory() {
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
        return resource.getDao();
    }

    public IXdStorageDaoResource lockResource(final XdStorageClassInfo clInfo, final Object id,
                                              final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStoragePolicy policy = clInfo.getPolicy();
        final Object resourceId = getNamingService().getResourceId(policy, clInfo.getClazz(), null, id);
        final IXdStorageResourceObject<IXdStorageDaoResource> resource = internalLockResource(new IResourceFactory() {
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
        return resource.getDao();
    }

    protected abstract IXdStorageResourceObject<IXdStorageDaoResource> createResource(final Object resourceId,
                                                                                      final XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockReferencesResource(final XdStorageClassInfo clInfo,
                                                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final Object resourceId = getNamingService().getIndexResourceId("references", policy, clInfo.getClazz());
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createReferencesResource(resourceId, clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.INDEX_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource.getDao();
    }

    protected abstract IXdStorageResourceObject<IXdStorageDaoResource> createReferencesResource(final Object resourceId,
                                                                                                final XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockClassResource(final Object object, final XdStorageClassInfo clInfo,
                                                   final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            final Object resourceId = getNamingService().getResourceId(policy, clInfo.getClazz(), null, null);
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
        return resource.getDao();
    }

    public <T> IXdStorageDaoResource lockObjectResource(final T object,
                                                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final Object resourceId = getNamingService().getResourceId(policy, cl, object, null);
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
        return resource.getDao();
    }

    public IXdStorageDaoResource lockObjectResource(final XdStorageClassInfo clInfo, final Object id,
                                                    final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final Object resourceId = getNamingService().getResourceId(policy, clInfo.getClazz(), null, id);
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
        return resource.getDao();
    }

    public IXdStorageSearchIndexDaoResource lockSearchIndexResource(final XdStorageClassInfo clInfo, final String indexName,
                                                                    final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStoragePolicy policy = clInfo.getPolicy();
        final Object resourceId = getNamingService().getIndexResourceId(indexName, policy, clInfo.getClazz());
        final IXdStorageSearchIndexResourceObject resource = internalLockResource(new IResourceFactory() {
            @Override
            public Object getResourceId() {
                return resourceId;
            }

            @Override
            public IXdStorageResourceObject create() {
                return createSearchIndexResource(resourceId, indexName, clInfo);
            }

            @Override
            public int getCommitOrder() {
                return XdStorageResourceCommitOrder.SEARCH_INDEX_RESOURCE_ORDER;
            }
        }, transaction);
        return resource.getDao();
    }

    protected abstract IXdStorageSearchIndexResourceObject createSearchIndexResource(final Object resourceId, String indexName, XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockStructureResource(final XdStorageClassInfo clInfo,
                                                       final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object resourceId = getNamingService().getStructureResourceId(clInfo.getClazz());
        final IXdStorageResourceObject<IXdStorageDaoResource> resource = internalLockResource(new IResourceFactory() {
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
        return resource.getDao();
    }

    protected interface IResourceFactory {

        Object getResourceId();

        IXdStorageResourceObject create();

        int getCommitOrder();
    }

    protected final <T extends IXdStorageResourceObject> T internalLockResource(final IResourceFactory factory,
                                                                                final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object resourceId = factory.getResourceId();
        AtomicLong counter = locks.get(resourceId);
        if (counter == null) {
            locks.putIfAbsent(resourceId, new AtomicLong(0));
            counter = locks.get(resourceId);
        }

        IXdStorageResourceObject returnResource;
        synchronized (counter) {
            returnResource = resources.get(resourceId);
            if (returnResource == null) {
                resources.put(resourceId, returnResource = factory.create());

                final Class<?> cl = returnResource.getObjectsClass();
                Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
                if (objectsCounters == null) {
                    countObjects.putIfAbsent(cl, new ConcurrentHashMap<>());
                    objectsCounters = countObjects.get(cl);
                }
                objectsCounters.put(resourceId, new AtomicLong(0));
            }
            if (!transaction.isResourceRegistered(returnResource)) {
                transaction.registerResource(returnResource, factory.getCommitOrder());
                counter.incrementAndGet();
            }
        }
        returnResource.prepare(transaction);
        return (T) returnResource;
    }

    public final void releaseResource(final IXdStorageResourceObject resource) {
        final Object resourceId = resource.getResourceId();
        final AtomicLong counter = locks.get(resourceId);
        synchronized (counter) {
            if (counter.decrementAndGet() == 0) {
                resources.remove(resourceId);
            }
        }
    }

    public void updateCounter(final Class<?> cl, final IXdStorageResourceObject<?> resource) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        final AtomicLong counter = objectsCounters.get(resource.getResourceId());
        counter.set(resource.getObjectsCount());
    }

    public void updateCounterByObjectAdded(final Class<?> cl, final IXdStorageResourceObject<?> resource) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        final AtomicLong counter = objectsCounters.get(resource.getResourceId());
        counter.incrementAndGet();
    }

    public void updateCounterByObjectRemoved(final Class<?> cl, final IXdStorageResourceObject<?> resource) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        final AtomicLong counter = objectsCounters.get(resource.getResourceId());
        counter.decrementAndGet();
    }

    public Object getFreeResourceId(final Class<?> cl, final long fragmentSize) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        if (objectsCounters != null) {
            for (final Map.Entry<Object, AtomicLong> entry : objectsCounters.entrySet()) {
                if (entry.getValue().get() < fragmentSize) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
