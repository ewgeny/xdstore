package org.flib.xdstorage.search;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.btree.IXdStorageBTreeViewer;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.IXdStorageChangeIndexRollback;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.search.data.XdStorageSearchKeyIndexRecord;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.search.key.XdStorageSearchIndexKey;
import org.flib.xdstorage.search.key.XdStorageSearchIndexOperationType;
import org.flib.xdstorage.serialization.IXdStorageIOFactory;
import org.flib.xdstorage.serialization.IXdStorageObjectsReader;
import org.flib.xdstorage.serialization.IXdStorageObjectsWriter;
import org.flib.xdstorage.services.XdStorageServicesLocator;
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

    @Override
    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;
        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), indexName);
        final XdStorageBTreeId keyBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "key_" + indexName);

        // Локальное ленивое чтение дерева в рамках текущей транзакции пользователя
        XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);

        // =========================================================================
        // КАНОНИЧЕСКОЕ ИСПРАВЛЕНИЕ (Полная ликвидация сторонних потоков и транзакций):
        // Если дерева поиска на диске еще нет, мы инициализируем и сохраняем его
        // СТРОГО в рамках текущей пользовательской транзакции! Никаких Executor и autonomousTx.
        // Блокировка createTreeLock защищает структуру в куче Java от гонок инициализации.
        // =========================================================================
        if (tree == null) {
            createTreeLock.lock();
            try {
                // Повторно проверяем внутри критической секции в контексте текущей транзакции
                tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
                if (tree == null) {
                    final XdStorageSearchIndex searchIndex = objectClInfo.getIndexes().get(indexName);

                    // Создаем и каскадно сохраняем дерево адресации ключей в ТЕКУЩУЮ транзакцию
                    XdStorageBTree subKeyTree = new XdStorageBTree(keyBTreeId, false, searchIndex.getIndexFillingValue());
                    storage.save(subKeyTree, transaction);

                    // Создаем и каскадно сохраняем основное дерево поиска в ТЕКУЩУЮ транзакцию
                    tree = new XdStorageBTree(searchBTreeId, false, searchIndex.getIndexFillingValue());
                    storage.save(tree, transaction);

                    // Обновляем метаданные самого индексного ресурса
                    storage.update(tree, transaction);
                    storage.update(subKeyTree, transaction);
                }
            } finally {
                createTreeLock.unlock();
            }
        }

        // Загружаем дерево ключей адресации в контексте текущей транзакции
        XdStorageBTree keyTree = storage.load(XdStorageBTree.class, keyBTreeId, transaction);
        if (tree == null || keyTree == null) {
            throw new XdStorageException("Сбой инициализации индексной структуры: деревья поиска не найдены");
        }

        final XdStorageSearchKeyIndexRecord hashIndexRecord = new XdStorageSearchKeyIndexRecord(record.getId(), record.getPrimaryIndexValue());

        // Атомарно пишем данные в индексы в рамках ЕДИНОЙ транзакции
        keyTree.insert((Comparable) record.getId(), hashIndexRecord, storage, transaction);
        final XdStorageSearchIndexKey searchIndexKey = new XdStorageSearchIndexKey(record.getPrimaryIndexValue(), record.getId(), XdStorageSearchIndexOperationType.Insert);
        tree.insert(searchIndexKey, XdStorageObjectUtils.getWrappedObjectOrSameObject(record), storage, transaction);
    }

    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId keyBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "key_" + indexName);

        // Fail-safe загрузка дерева ключей
        XdStorageBTree keyTree = storage.load(XdStorageBTree.class, keyBTreeId, transaction);
        if (keyTree == null) {
            // =========================================================================
            // ИНФРАСТРУКТУРНЫЙ МОСТ: Если MVCC-барьер скрыл глобальное дерево индексов,
            // загружаем его без транзакционного паспорта напрямую с диска, чтобы спасти Писателя!
            // =========================================================================
            keyTree = storage.load(XdStorageBTree.class, keyBTreeId);
        }
        if (keyTree == null) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " does not exists");
        }

        final List<Object> findResult = keyTree.find((Comparable) record.getId(), storage, transaction);
        if (findResult == null || findResult.isEmpty()) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " does not exists in index keys");
        }

        final XdStorageSearchKeyIndexRecord oldHashIndexRecord = (XdStorageSearchKeyIndexRecord) findResult.get(0);
        final Object oldPrimaryFieldValue = oldHashIndexRecord.getValue(), newPrimaryFieldValue = record.getPrimaryIndexValue();

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), indexName);

        // Fail-safe загрузка основного дерева поиска
        XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
        if (tree == null) {
            tree = storage.load(XdStorageBTree.class, searchBTreeId); // Загружаем глобальный мета-объект напрямую
        }
        if (tree == null) {
            throw new XdStorageException("object with idgeneration " + record.getId() + " does not exists");
        }

        if ((oldPrimaryFieldValue == null && newPrimaryFieldValue == null)
                || (oldPrimaryFieldValue != null && oldPrimaryFieldValue.equals(newPrimaryFieldValue))) {
            final XdStorageSearchIndexKey searchIndexKey = new XdStorageSearchIndexKey(oldHashIndexRecord.getValue(), oldHashIndexRecord.getObjectId(), XdStorageSearchIndexOperationType.Update);
            tree.update(searchIndexKey, record, storage, transaction);
        } else {
            final XdStorageSearchKeyIndexRecord newHashIndexRecord = new XdStorageSearchKeyIndexRecord(record.getId(), record.getPrimaryIndexValue());

            keyTree.update((XdStorageSearchIndexKey) record.getId(), newHashIndexRecord, storage, transaction);

            final XdStorageSearchIndexKey searchIndexKeyToDelete = new XdStorageSearchIndexKey(oldHashIndexRecord.getValue(), oldHashIndexRecord.getObjectId(), XdStorageSearchIndexOperationType.Delete);
            tree.delete(searchIndexKeyToDelete, storage, transaction);
            final XdStorageSearchIndexKey searchIndexKeyToInsert = new XdStorageSearchIndexKey(newHashIndexRecord.getValue(), newHashIndexRecord.getObjectId(), XdStorageSearchIndexOperationType.Insert);
            tree.insert(searchIndexKeyToInsert, record, storage, transaction);
        }
    }

    @Override
    public void delete(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId keyBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), "key_" + indexName);

        XdStorageBTree keyTree = storage.load(XdStorageBTree.class, keyBTreeId, transaction);
        if (keyTree == null) {
            keyTree = storage.load(XdStorageBTree.class, keyBTreeId);
        }
        if (keyTree == null) {
            return;
        }

        final List<Object> findResult = keyTree.find((Comparable) record.getId(), storage, transaction);
        if (findResult.isEmpty()) {
            return;
        }

        final XdStorageSearchKeyIndexRecord oldHashIndexRecord = (XdStorageSearchKeyIndexRecord) findResult.get(0);
        keyTree.delete((Comparable) record.getId(), storage, transaction);

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(objectClInfo.getClazz(), indexName);

        XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
        if (tree == null) {
            tree = storage.load(XdStorageBTree.class, searchBTreeId); // Инфраструктурный фолбэк-запрос
        }
        if (tree == null) {
            return;
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
    public IXdStorageSearchIndexDaoResource getDao() {
        return this;
    }
}
