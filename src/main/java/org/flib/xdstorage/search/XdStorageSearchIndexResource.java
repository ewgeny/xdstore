package org.flib.xdstorage.search;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.btree.IXdStorageBTreeViewer;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.index.IXdStorageChangeIndexRollback;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.search.data.XdStorageSearchKeyIndexRecord;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.search.key.XdStorageSearchIndexKey;
import org.flib.xdstorage.search.key.XdStorageSearchIndexOperationType;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.serialization.IXdStorageIOFactory;
import org.flib.xdstorage.serialization.IXdStorageObjectsReader;
import org.flib.xdstorage.serialization.IXdStorageObjectsWriter;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageSearchIndexResource implements IXdStorageSearchIndexResourceObject, IXdStorageSearchIndexDaoResource {

    protected final XdStorageServicesLocator services;

    protected final XdStorageAbstractResourcesManager manager;

    protected final String indexName;

    protected final Object resourceId;

    protected final XdStorageClassInfo objectClInfo;

    protected final XdStorageClassInfo clInfo;

    protected final XdStorageObjectIdField idField;

    private Map<String, Stack<IXdStorageChangeIndexRollback>> rollbacks = new ConcurrentHashMap<String, Stack<IXdStorageChangeIndexRollback>>();

    private final IXdStorageObjectsReader reader;

    private final IXdStorageObjectsWriter writer;

    public XdStorageSearchIndexResource(final XdStorageServicesLocator services,
                                        final XdStorageAbstractResourcesManager manager,
                                        final String indexName, final Object resourceId,
                                        final XdStorageClassInfo objectClInfo, final IXdStorageIOFactory factory, final int fragmentSize) {
        this.services = services;
        this.manager = manager;
        this.indexName = indexName;
        this.resourceId = resourceId;
        this.objectClInfo = objectClInfo;
        this.clInfo = XdStorageObjectUtils.getClassInfo(XdStorageSearchKeyIndexRecord.class);
        this.idField = clInfo.getIdField();
        this.reader = factory.newInstanceReader();
        this.writer = factory.newInstanceWriter();
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
        return 0;
    }

    @Override
    public boolean hasChanges(final XdStorageTransaction transaction) throws XdStorageException {
        return false;
    }

    public String getFileName() {
        return resourceId.toString();
    }

    public void prepare(final XdStorageTransaction transaction) {
        // do nothing
    }

    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) {
        // do nothing
    }

    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageException {
        // do nothing
    }

    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException {
        // do nothing
    }

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
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
        manager.releaseResource(this);
    }

    private final Lock createTreeLock = new ReentrantLock();

    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;

        // insert search index record into search btree
        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), indexName);
        final XdStorageBTreeId keyBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "key_" + indexName);

        XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
        XdStorageBTree keyTree;
        if (tree == null) {
            createTreeLock.lock();
            try {
                tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
                if (tree == null) {
                    final XdStorageSearchIndex searchIndex = objectClInfo.getIndexes().get(indexName);

                    keyTree = new XdStorageBTree(keyBTreeId, false, searchIndex.getIndexFillingValue());
                    storage.save(keyTree, transaction);

                    tree = new XdStorageBTree(searchBTreeId, false, searchIndex.getIndexFillingValue());
                    storage.save(tree, transaction);
                } else {
                    keyTree = storage.load(XdStorageBTree.class, keyBTreeId, transaction);
                }
            } finally {
                createTreeLock.unlock();
            }
        } else {
            keyTree = storage.load(XdStorageBTree.class, keyBTreeId, transaction);
        }

        final XdStorageSearchKeyIndexRecord hashIndexRecord = new XdStorageSearchKeyIndexRecord(record.getId(), record.getPrimaryIndexValue());

        keyTree.insert((Comparable) record.getId(), hashIndexRecord, storage, transaction);
        final XdStorageSearchIndexKey searchIndexKey = new XdStorageSearchIndexKey(record.getPrimaryIndexValue(), record.getId(), XdStorageSearchIndexOperationType.Insert);
        tree.insert(searchIndexKey, XdStorageObjectUtils.getWrappedObjectOrSameObject(record), storage, transaction);
    }

    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId keyBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "key_" + indexName);

        final XdStorageBTree keyTree = storage.load(XdStorageBTree.class, keyBTreeId, transaction);
        if (keyTree == null) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " does not exists");
        }

        final List<Object> keyFindResult = keyTree.find((Comparable) record.getId(), storage, transaction);
        if (keyFindResult.isEmpty()) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " is not found");
        }
        final XdStorageSearchKeyIndexRecord oldHashIndexRecord = (XdStorageSearchKeyIndexRecord) keyFindResult.get(0);
        final Object oldPrimaryFieldValue = oldHashIndexRecord.getValue(), newPrimaryFieldValue = record.getPrimaryIndexValue();

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), indexName);

        final XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
        if (tree == null) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " does not exists");
        }

        if ((oldPrimaryFieldValue == null && newPrimaryFieldValue == null)
                || (oldPrimaryFieldValue != null && oldPrimaryFieldValue.equals(newPrimaryFieldValue))) {
            final XdStorageSearchIndexKey searchIndexKey = new XdStorageSearchIndexKey(oldHashIndexRecord.getValue(), oldHashIndexRecord.getObjectId(), XdStorageSearchIndexOperationType.Update);
            tree.update(searchIndexKey, record, storage, transaction);
        } else {
            final XdStorageSearchKeyIndexRecord newHashIndexRecord = new XdStorageSearchKeyIndexRecord(record.getId(), record.getPrimaryIndexValue());

            keyTree.update((Comparable) record.getId(), newHashIndexRecord, storage, transaction);

            final XdStorageSearchIndexKey searchIndexKeyToDelete = new XdStorageSearchIndexKey(oldHashIndexRecord.getValue(), oldHashIndexRecord.getObjectId(), XdStorageSearchIndexOperationType.Delete);
            tree.delete(searchIndexKeyToDelete, storage, transaction);
            final XdStorageSearchIndexKey searchIndexKeyToInsert = new XdStorageSearchIndexKey(newHashIndexRecord.getValue(), newHashIndexRecord.getObjectId(), XdStorageSearchIndexOperationType.Insert);
            tree.insert(searchIndexKeyToInsert, record, storage, transaction);
        }
    }

    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId keyBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "key_" + indexName);

        final XdStorageBTree keyTree = storage.load(XdStorageBTree.class, keyBTreeId, transaction);
        if (keyTree == null) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " does not exists");
        }

        final List<Object> findResult = keyTree.find((Comparable) record.getId(), storage, transaction);
        if (findResult.isEmpty()) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " is not found");
        }

        final XdStorageSearchKeyIndexRecord oldHashIndexRecord = (XdStorageSearchKeyIndexRecord) findResult.get(0);
        keyTree.delete((Comparable) record.getId(), storage, transaction);

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), indexName);

        final XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
        if (tree == null) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " does not exists");
        }

        final XdStorageSearchIndexKey searchIndexKey = new XdStorageSearchIndexKey(oldHashIndexRecord.getValue(), oldHashIndexRecord.getObjectId(), XdStorageSearchIndexOperationType.Insert);
        tree.delete(searchIndexKey, storage, transaction);
    }

    public <T> void watch(final XdStorageTransaction transaction, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException {
        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), indexName);

        final XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
        if (tree != null) {
            tree.read(storage, transaction, new IXdStorageBTreeViewer() {
                @Override
                public void look(Comparable comparable, Object value) throws XdStorageException, XdStorageConnectionException {
                    watcher.watch((T) value);
                }
            });
        }
    }

    @Override
    public List<IXdStorageSimpleWrapper> selectObjectReferences(XdStorageClassInfo clInfo, XdStorageSqlSearchQuery query, XdStorageTransaction transaction) throws XdStorageException {
        throw new XdStorageException("Unsupported operation exception");
    }

    @Override
    public IXdStorageSearchIndexDaoResource getDao() {
        return this;
    }
}
