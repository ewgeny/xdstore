package org.flib.xdstorage.index.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.btree.IXdStorageBTreeViewer;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.index.IXdStorageIndexResourceObject;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageResourceCache;
import org.flib.xdstorage.serialization.IXdStorageObjectsReader;
import org.flib.xdstorage.serialization.IXdStorageObjectsWriter;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageBTreeIndexResource implements IXdStorageIndexResourceObject, IXdStorageIndexDaoResource {

    protected final XdStorageServicesLocator services;

    protected final XdStorageAbstractResourcesManager manager;

    protected final XdStorageResourceCache cache;

    protected final Object resourceId;

    protected final String indexName;

    private final long fragmentSize;

    protected final XdStorageClassInfo clInfo;

    protected final XdStorageObjectIdField idField;

    protected final XdStorageClassInfo objectClInfo;

    protected final XdStorageObjectIdField objectIdField;

    private final IXdStorageObjectsReader reader;

    private final IXdStorageObjectsWriter writer;

    private final AtomicBoolean prepared = new AtomicBoolean(false);

    private final Lock lock = new ReentrantLock();

    public XdStorageBTreeIndexResource(final XdStorageServicesLocator services,
                                       final XdStorageAbstractResourcesManager manager,
                                       final Object resourceId, final String indexName,
                                       final XdStorageClassInfo objectClassInfo, final int fragmentSize) {
        this.services = services;
        this.manager = manager;
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.fragmentSize = fragmentSize;
        this.objectClInfo = objectClassInfo;
        this.objectIdField = objectClassInfo.getIdField();

        this.clInfo = XdStorageObjectUtils.getClassInfo(XdStorageBTree.class);
        this.idField = this.clInfo.getIdField();

        this.cache = new XdStorageResourceCache(services, this.clInfo.getClazz(), this.idField);

        this.reader = services.getIoFactory().newInstanceReader();
        this.writer = services.getIoFactory().newInstanceWriter();
    }

    @Override
    public Object getResourceId() {
        return resourceId;
    }

    @Override
    public Class<?> getObjectsClass() {
        return XdStorageBTree.class;
    }

    @Override
    public long getObjectsCount() {
        return cache.getObjectsCount();
    }

    @Override
    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
        return cache.hasChanges(transaction);
    }

    private Map<String, Long> blockingTime = new HashMap<>();

    private AtomicBoolean locked = new AtomicBoolean(false);

    @Override
    public void lockForCommit(final XdStorageTransaction transaction) {
        synchronized (locked) {
            while (locked.get()) {
                try {
                    final String transactionId = transaction.getTransactionId();

                    final long timeout = 2 * transaction.getTimeout();
                    final Long startTime = blockingTime.remove(transactionId);
                    final Long currentTime = System.currentTimeMillis();
                    if (startTime == null) {
                        blockingTime.put(transactionId, currentTime);
                    } else if ( (currentTime - startTime) >= timeout ) {
                        throw new XdStorageRuntimeException("resource " + getFileName() + " cannot be locked for commit by transaction "
                                + transactionId + " and transaction should be rolled back");
                    } else {
                        blockingTime.put(transactionId, startTime);
                    }

                    locked.wait(timeout / 2);
                } catch (final InterruptedException e) {
                    throw new XdStorageRuntimeException("waiting for lock resource " + getFileName() + " has been interrupted", e);
                }
            }
            locked.set(true);
        }
    }

    @Override
    public void unlockAfterCommit(final XdStorageTransaction transaction) {
        synchronized (locked) {
            locked.set(false);
            locked.notify();
        }
    }

    public String getFileName() {
        return resourceId.toString();
    }

    private Collection<Object> readAsObjects() {
        Collection<Object> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new FileReader(file);
                try {
                    objects = reader.read(xmlReader);
                } finally {
                    xmlReader.close();
                }
            }
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("invalid state of database: error by reading resource " + resourceId, e);
        }
        return objects;
    }

    private void writeObjects(final Collection<Object> objects) {
        File file = new File(getFileName());
        if (file.exists())
            file.delete();

        if (objects.size() > 0) {
            try {
                // TODO : REVIEW file deleting and creation
                // ! and think about backup file
                file = new File(getFileName());
                if (!file.exists()) {
                    final File parentFile = file.getParentFile();
                    if (parentFile != null && !parentFile.exists())
                        parentFile.mkdirs();
                    file.createNewFile();
                }

                Writer xmlWriter = null;
                try {
                    xmlWriter = new FileWriter(file);

                    writer.writeObjects(xmlWriter, objects);
                } finally {
                    xmlWriter.close();
                }
            } catch (final Throwable e) {
                throw new XdStorageRuntimeException("invalid state of database: error by writing resource " + resourceId, e);
            }
        }
    }

    public Collection<XdStorageIdentifiableObject> readAsData(final XdStorageTransaction transaction) {
        Collection<XdStorageIdentifiableObject> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new FileReader(file);
                try {
                    objects = reader.readData(xmlReader, idField);
                } finally {
                    xmlReader.close();
                }
            }
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("invalid state of database: error by reading resource " + resourceId, e);
        }
        return objects;
    }

    public void prepare(final XdStorageTransaction transaction) {
        if (prepared.get()) {
            return;
        }

        lock.lock();
        try {
            if (prepared.get()) {
                return;
            }

            Collection<Object> objects = readAsObjects();

            if (objects != null)
                cache.fillCache(objects);

            prepared.set(true);
        } finally {
            lock.unlock();
        }
    }

    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageException {
        if (cache.hasChanges(transaction)) {
            final Collection<Object> objects = cache.readUnsafe(transaction);
            cache.prepareCommit(transaction, record);
            writeObjects(objects);
        }
    }

    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageException {
        cache.performCommit(transaction, record);
        cache.commit(transaction);
    }

    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException {
        cache.rollbackFailedCommit(transaction, changes);
        writeObjects(cache.readUnsafe(transaction));
    }

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        cache.rollback(transaction);
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        cache.clear(transaction);
        manager.releaseResource(this);
    }

    public Object getObjectResourceId(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final IXdStorage storage = services.getStorage();
        final XdStorageBTreeId bTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "id_index");
        final XdStorageBTree tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
        if (tree != null) {
            final List<Object> result = tree.find((Comparable) id, storage, transaction);
            return result.isEmpty() ? null : result.get(0);
        }
        return null;
    }

    @Override
    public boolean has(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        return getObjectResourceId(id, transaction) != null;
    }

    @Override
    public <T> Collection<T> read(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final List<T> result = new ArrayList<>();

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId bTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "id_index");
        final XdStorageBTree tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
        if (tree != null) {
            tree.read(storage, transaction, new IXdStorageBTreeViewer() {
                @Override
                public void look(final Comparable objectId, final Object resourceId) throws XdStorageException, XdStorageConnectionException {
                    final IXdStorageDaoResource resource = manager.lockResource(resourceId, objectClInfo, transaction);
                    result.add((T) resource.read(objectId, transaction));
                }
            });
        }

        return result;
    }

    @Override
    public <T> Collection<T> read(final XdStorageTransaction transaction, final IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException {
        final List<T> result = new ArrayList<>();

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId bTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "id_index");
        final XdStorageBTree tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
        if (tree != null) {
            final XdStorageException exceptions[] = new XdStorageException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);
            tree.read(storage, transaction, new IXdStorageBTreeViewer() {
                @Override
                public void look(final Comparable objectId, final Object resourceId) throws XdStorageException, XdStorageConnectionException {
                    if (hasError.get()) {
                        return;
                    }

                    try {
                        final IXdStorageDaoResource resource = manager.lockResource(resourceId, objectClInfo, transaction);
                        final T object = (T) resource.read(objectId, transaction);
                        if (predicate.passed(XdStorageObjectUtils.wrapAsUnmodifiableObject(object, storage, transaction))) {
                            result.add(object);
                        }
                    } catch (final Throwable e) {
                        if (hasError.get()) {
                            return;
                        }
                        hasError.set(true);
                        exceptions[0] = new XdStorageException(e);
                    }
                }
            });
            if (exceptions[0] != null)
                throw exceptions[0];
        }

        return result;
    }

    @Override
    public <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException {
        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId bTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "id_index");
        final XdStorageBTree tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
        if (tree != null) {
            tree.read(storage, transaction, new IXdStorageBTreeViewer() {
                @Override
                public void look(final Comparable objectId, final Object resourceId) throws XdStorageException, XdStorageConnectionException {
                    final IXdStorageDaoResource resource = manager.lockResource(resourceId, objectClInfo, transaction);
                    final T object = (T) resource.read(objectId, transaction);
                    watcher.watch(XdStorageObjectUtils.wrapAsUnmodifiableObject(object, storage, transaction));
                }
            });
        }
    }

    @Override
    public void readByReference(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = objectIdField.get(reference);
        final Object resourceId = getObjectResourceId(objectId, transaction);
        if (resourceId == null) {
            throw new XdStorageException("cannot load by reference object of class " + reference.getClass() + " with idgeneration " + objectId);
        }
        final IXdStorageDaoResource resource = manager.lockResource(resourceId, objectClInfo, transaction);
        resource.readByReference(reference, transaction);
    }

    @Override
    public Object read(final Object id, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object resourceId = getObjectResourceId(id, transaction);
        if (resourceId == null) {
            return null;
        }
        final IXdStorageDaoResource resource = manager.lockResource(resourceId, objectClInfo, transaction);
        return resource.read(id, transaction);
    }

    private final Lock createTreeLock = new ReentrantLock();

    @Override
    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageIdGenerator idGenerator = services.getIdGenerator();

        Object objectId = objectIdField.get(object);
        if (objectId == null &&
                (objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR
                        || objectIdField.getIdGeneretorType() == XdStorageIdGeneratorType.CUSTOM_GENERATOR)
                && idGenerator != null) {
            objectIdField.set(XdStorageObserverService.getObservableWrapper(object), objectId = idGenerator.generate(object.getClass(), services.getStorage(), transaction));
        }

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId bTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "id_index");
        XdStorageBTree tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
        if (tree == null) {
            createTreeLock.lock();
            try {
                tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
                if (tree == null) {
                    tree = new XdStorageBTree(bTreeId, false, objectClInfo.getIndexFillingValue());
                    storage.save(tree, transaction);
                }
            } finally {
                createTreeLock.unlock();
            }
        }

        final Object resourceId = manager.getFreeResourceId(objectClInfo.getClazz(), fragmentSize);
        final IXdStorageDaoResource resource;
        if (resourceId == null) {
            resource = manager.lockResource(objectClInfo, objectId, transaction);
        } else {
            resource = manager.lockResource(resourceId, objectClInfo, transaction);
        }

        resource.insert(object, transaction);

        tree.insert((Comparable) objectId, resource.getResourceId(), storage, transaction);
    }

    @Override
    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = objectIdField.get(object);

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId bTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "id_index");
        final XdStorageBTree tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
        if (tree == null) {
            throw new XdStorageException("object of class " + object.getClass() + " with idgeneration " + objectId + " does not exists");
        }

        final List<Object> result = tree.find((Comparable) objectId, storage, transaction);
        final Object resourceId = result.isEmpty() ? null : result.get(0);
        if (resourceId == null) {
            throw new XdStorageException("object of class " + object.getClass() + " with idgeneration " + objectId + " does not exists");
        }

        final IXdStorageDaoResource resource = manager.lockResource(resourceId, objectClInfo, transaction);
        resource.update(object, transaction);
    }

    @Override
    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object objectId = objectIdField.get(object);

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId bTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "id_index");
        final XdStorageBTree tree = storage.load(XdStorageBTree.class, bTreeId, transaction);
        if (tree == null) {
            throw new XdStorageException("object of class " + object.getClass() + " with idgeneration " + objectId + " does not exists");
        }

        final List<Object> result = tree.find((Comparable) objectId, storage, transaction);
        final Object resourceId = result.isEmpty() ? null : result.get(0);
        if (resourceId == null) {
            throw new XdStorageException("object of class " + object.getClass() + " with idgeneration " + objectId + " does not exists");
        }

        final IXdStorageDaoResource resource = manager.lockResource(resourceId, objectClInfo, transaction);
        resource.delete(object, transaction);

        tree.delete((Comparable) objectId, storage, transaction);
    }

    @Override
    public IXdStorageIndexDaoResource getDao() {
        return this;
    }
}
