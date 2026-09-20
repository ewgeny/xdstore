package org.flib.xdstorage.resource;

import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Collection;

public abstract class XdStorageAbstractResource implements IXdStorageResourceObject<IXdStorageDaoResource>, IXdStorageDaoResource {

    protected final XdStorageServicesLocator services;

    protected final XdStorageAbstractResourcesManager manager;

    protected final Object resourceId;

    protected final IXdStorageIdGenerator idGenerator;

    protected final XdStorageClassInfo clInfo;

    protected final XdStorageObjectIdField idField;

    protected final boolean isReferences;

    protected final XdStorageResourceCache cache;

    protected XdStorageAbstractResource(final XdStorageServicesLocator services,
                                        final XdStorageAbstractResourcesManager manager,
                                        final Object resourceId,
                                        final XdStorageClassInfo clInfo) {
        this(services, manager, resourceId, clInfo, null, false);
    }

    protected XdStorageAbstractResource(final XdStorageServicesLocator services,
                                        final XdStorageAbstractResourcesManager manager,
                                        final Object resourceId,
                                        final XdStorageClassInfo clInfo,
                                        final boolean isReferences) {
        this(services, manager, resourceId, clInfo, null, isReferences);
    }

    protected XdStorageAbstractResource(final XdStorageServicesLocator services,
                                        final XdStorageAbstractResourcesManager manager,
                                        final Object resourceId,
                                        final XdStorageClassInfo clInfo,
                                        final IXdStorageIdGenerator idGenerator,
                                        final boolean isReferences) {
        this.services = services;
        this.manager = manager;
        this.resourceId = resourceId;
        this.clInfo = clInfo;
        this.idField = clInfo.getIdField();
        this.isReferences = isReferences;
        this.cache = new XdStorageResourceCache(services, clInfo.getClazz(), idField);
        this.idGenerator = idGenerator;
    }

    public XdStorageAbstractResourcesManager getManager() {
        return manager;
    }

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

    protected void postPrepare(final XdStorageTransaction transaction) {
        // do nothing
    }

    protected void postCommit(final XdStorageTransaction transaction) {
        // do nothing
    }

    protected void postRollback(final XdStorageTransaction transaction) {
        // do nothing
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

    public Collection<XdStorageIdentifiableObject> readAsData(final XdStorageTransaction transaction) throws XdStorageException {
        throw new XdStorageRuntimeException("unsupported operation exception");
    }

    public boolean hasObject(final Object objectId, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        return cache.hasObject(objectId);
    }

    public Collection<Object> readReferences(final XdStorageTransaction transaction) throws XdStorageException {
        return cache.read(transaction);
    }

    public void insertReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        Object objectId = idField.get(reference);
        if ((idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                || idField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                && objectId == null && idGenerator != null) {
            idField.set(reference, idGenerator.generate(reference.getClass(), services.getStorage(), transaction));
        }
        cache.insert(reference, transaction);
        manager.updateCounterByObjectAdded(clInfo.getClazz(), this);
    }

    public void deleteReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException {
        cache.delete(idField.get(reference), transaction);
        manager.updateCounterByObjectRemoved(clInfo.getClazz(), this);
    }

    public <T> T find(final Object object, final XdStorageTransaction transaction) throws XdStorageException {
        return cache.findUnidentified(object, transaction);
    }

    public <T> Collection<T> read(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        return cache.read(transaction);
    }

    public <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException {
        return cache.read(transaction, predicate);
    }

    public <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException {
        cache.watch(transaction, watcher);
    }

    public void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        cache.readByReference(reference, transaction);
    }

    public Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        return cache.read(id, transaction);
    }

    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        cache.insert(object, transaction);
        manager.updateCounterByObjectAdded(clInfo.getClazz(), this);
    }

    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        cache.update(object, transaction);
    }

    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        cache.delete(idField.get(object), transaction);
        manager.updateCounterByObjectRemoved(clInfo.getClazz(), this);
    }

    @Override
    public IXdStorageDaoResource getDao() {
        return this;
    }
}
