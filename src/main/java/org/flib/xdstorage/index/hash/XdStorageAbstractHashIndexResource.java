package org.flib.xdstorage.index.hash;

import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.index.IXdStorageChangeIndexRollback;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.index.IXdStorageIndexResourceObject;
import org.flib.xdstorage.index.XdStorageIndexResourceCache;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageResourceCache;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class XdStorageAbstractHashIndexResource implements IXdStorageIndexResourceObject, IXdStorageIndexDaoResource {

    protected final XdStorageServicesLocator services;

    protected final XdStorageAbstractResourcesManager manager;

    protected final Object resourceId;

    protected final IXdStorageIdGenerator idGenerator;

    protected final XdStorageClassInfo clInfo;

    protected final XdStorageObjectIdField idField;

    protected final boolean isReferences;

    protected final XdStorageResourceCache cache;

    protected final String indexName;

    protected final XdStorageIndexResourceCache index;

    protected final Map<String, Stack<IXdStorageChangeIndexRollback>> rollbacks = new ConcurrentHashMap<String, Stack<IXdStorageChangeIndexRollback>>();

    protected final XdStorageClassInfo objectClInfo;

    protected final XdStorageObjectIdField objectIdField;

    protected XdStorageAbstractHashIndexResource(final XdStorageServicesLocator services,
                                                 final XdStorageAbstractResourcesManager manager,
                                                 final Object resourceId, final String indexName,
                                                 final XdStorageClassInfo clInfo, final int fragmentSize) {
        this.services = services;
        this.manager = manager;
        this.resourceId = resourceId;
        this.clInfo = XdStorageObjectUtils.getClassInfo(XdStorageHashIndexRecord.class);
        this.idField = this.clInfo.getIdField();
        this.isReferences = false;
        this.cache = new XdStorageResourceCache(services, clInfo.getClazz(), idField);
        this.idGenerator = null;

        this.indexName = indexName;
        this.index = new XdStorageIndexResourceCache(fragmentSize);
        this.objectClInfo = clInfo;
        this.objectIdField = clInfo.getIdField();
    }

    protected XdStorageAbstractHashIndexResource(final XdStorageServicesLocator services,
                                                 final XdStorageAbstractResourcesManager manager,
                                                 final Object resourceId, final String indexName,
                                                 final XdStorageClassInfo clInfo, final IXdStorageIdGenerator idGenerator,
                                                 final int fragmentSize) {
        this.services = services;
        this.manager = manager;
        this.resourceId = resourceId;
        this.clInfo = XdStorageObjectUtils.getClassInfo(XdStorageHashIndexRecord.class);
        this.idField = this.clInfo.getIdField();
        this.isReferences = false;
        this.cache = new XdStorageResourceCache(services, clInfo.getClazz(), idField);
        this.idGenerator = idGenerator;

        this.indexName = indexName;
        this.index = new XdStorageIndexResourceCache(fragmentSize);
        this.objectClInfo = clInfo;
        this.objectIdField = clInfo.getIdField();
    }

    @Override
    public Object getResourceId() {
        return resourceId;
    }

    @Override
    public Class<?> getObjectsClass() {
        return clInfo.getClazz();
    }

    @Override
    public long getObjectsCount() {
        return cache.getObjectsCount();
    }

    @Override
    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
        return cache.hasChanges(transaction);
    }

    protected void postPrepare(final XdStorageTransaction transaction) throws XdStorageException {
        if (index.isClear()) {
            final Collection<Object> objects = cache.read(transaction);
            for (final Object object : objects) {
                final XdStorageHashIndexRecord record = (XdStorageHashIndexRecord) object;
                index.insertRecord(record.getObjectId(), record.getResourceId());
            }
        }
    }

    protected void postCommit(final XdStorageTransaction transaction) {
        rollbacks.remove(transaction.getTransactionId());
    }

    protected void postRollback(final XdStorageTransaction transaction) {
        final Stack<IXdStorageChangeIndexRollback> stack = rollbacks.remove(transaction.getTransactionId());
        if (stack != null) {
            while (!stack.isEmpty()) {
                stack.pop().rollback();
            }
        }
    }

    protected void registerRollback(final XdStorageTransaction transaction, final IXdStorageChangeIndexRollback rb) {
        Stack<IXdStorageChangeIndexRollback> stack = rollbacks.get(transaction.getTransactionId());
        if (stack == null) {
            rollbacks.put(transaction.getTransactionId(), stack = new Stack<IXdStorageChangeIndexRollback>());
        }
        stack.push(rb);
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

    public Object getObjectResourceId(final Object id, final XdStorageTransaction transaction) {
        return index.getResourceId(id);
    }

    public boolean has(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        return index.getResourceId(id) != null;
    }

    public Collection<Object> read(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final List<Object> result = new ArrayList<>();
        final Collection<Object> ids = index.getResourcesIds();
        for (final Object resourceId : ids) {
            final XdStoragePolicy policy = objectClInfo.getPolicy();
            final IXdStorageDaoResource resource;
            if (policy == XdStoragePolicy.StoreWithParentObject) {
                resource = ((XdStorageSQLResourcesManager) manager).lockChildrenClassResource(objectClInfo, resourceId, transaction);
            } else {
                resource = manager.lockResource(resourceId, objectClInfo, transaction);
            }
            result.addAll(resource.read(transaction));
        }
        return result;
    }

    public <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException {
        final List<T> result = new ArrayList<>();
        final Collection<Object> ids = index.getResourcesIds();
        for (final Object resourceId : ids) {
            final XdStoragePolicy policy = objectClInfo.getPolicy();
            final IXdStorageDaoResource resource;
            if (policy == XdStoragePolicy.StoreWithParentObject) {
                resource = ((XdStorageSQLResourcesManager) manager).lockChildrenClassResource(objectClInfo, resourceId, transaction);
            } else {
                resource = manager.lockResource(resourceId, objectClInfo, transaction);
            }
            result.addAll(resource.read(transaction, predicate));
        }
        return result;
    }

    public <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException {
        final Collection<Object> ids = index.getResourcesIds();
        for (final Object resourceId : ids) {
            final XdStoragePolicy policy = objectClInfo.getPolicy();
            final IXdStorageDaoResource resource;
            if (policy == XdStoragePolicy.StoreWithParentObject) {
                resource = ((XdStorageSQLResourcesManager) manager).lockChildrenClassResource(objectClInfo, resourceId, transaction);
            } else {
                resource = manager.lockResource(resourceId, objectClInfo, transaction);
            }
            resource.watch(transaction, watcher);
        }
    }

    public void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = objectIdField.get(reference);
        final Object resourceId = index.getResourceId(objectId);
        if (resourceId == null) {
            throw new XdStorageException("cannot load by reference object of class " + reference.getClass() + " with idgeneration " + objectId);
        }

        final XdStoragePolicy policy = objectClInfo.getPolicy();
        final IXdStorageDaoResource resource;
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            resource = ((XdStorageSQLResourcesManager) manager).lockChildrenClassResource(objectClInfo, resourceId, transaction);
        } else {
            resource = manager.lockResource(resourceId, objectClInfo, transaction);
        }
        resource.readByReference(reference, transaction);
    }

    public Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object resourceId = index.getResourceId(id);
        if (resourceId == null) {
            return null;
        }

        final XdStoragePolicy policy = objectClInfo.getPolicy();
        final IXdStorageDaoResource resource;
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            resource = ((XdStorageSQLResourcesManager) manager).lockChildrenClassResource(objectClInfo, resourceId, transaction);
        } else {
            resource = manager.lockResource(resourceId, objectClInfo, transaction);
        }
        return resource.read(id, transaction);
    }

    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = objectIdField.get(object);
        boolean isInserted = false;
        final Object freeResourceId = index.getFreeResourceId();
        if (freeResourceId != null) {
            final IXdStorageDaoResource resource = manager.lockResource(freeResourceId, objectClInfo, transaction);
            resource.insert(object, transaction);

            final XdStorageHashIndexRecord record = new XdStorageHashIndexRecord(objectId, resource.getResourceId());
            cache.insert(record, transaction);

            index.insertRecord(objectId, resource.getResourceId());
            registerRollback(transaction, new IXdStorageChangeIndexRollback() {

                @Override
                public void rollback() {
                    index.deleteRecord(objectId);
                }
            });
            isInserted = true;
        }
        if (!isInserted) {
            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(object.getClass());
            final IXdStorageDaoResource resource = manager.lockResource(clInfo, objectId, transaction);
            resource.insert(object, transaction);

            final XdStorageHashIndexRecord record = new XdStorageHashIndexRecord(objectId, resource.getResourceId());
            cache.insert(record, transaction);

            index.insertRecord(objectId, resource.getResourceId());
            registerRollback(transaction, new IXdStorageChangeIndexRollback() {

                @Override
                public void rollback() {
                    index.deleteRecord(objectId);
                }
            });
        }
    }

    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = objectIdField.get(object);
        final Object resourceId = index.getResourceId(objectId);
        if (resourceId == null) {
            throw new XdStorageException("object of class " + object.getClass() + " with idgeneration " + objectId + " does not exists");
        }

        final XdStoragePolicy policy = objectClInfo.getPolicy();
        final IXdStorageDaoResource resource;
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            resource = ((XdStorageSQLResourcesManager) manager).lockChildrenClassResource(objectClInfo, resourceId, transaction);
        } else {
            resource = manager.lockResource(resourceId, objectClInfo, transaction);
        }
        resource.update(object, transaction);
    }

    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = objectIdField.get(object);
        final Object resourceId = index.getResourceId(objectId);
        if (resourceId == null) {
            throw new XdStorageException("object of class " + object.getClass() + " with idgeneration " + objectId + " does not exists");
        }

        final XdStoragePolicy policy = objectClInfo.getPolicy();
        final IXdStorageDaoResource resource;
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            resource = ((XdStorageSQLResourcesManager) manager).lockChildrenClassResource(objectClInfo, resourceId, transaction);
        } else {
            resource = manager.lockResource(resourceId, objectClInfo, transaction);
        }
        resource.delete(object, transaction); // will be rolled back

        cache.delete(objectId, transaction);
    }

    @Override
    public IXdStorageIndexDaoResource getDao() {
        return this;
    }
}
