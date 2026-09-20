package org.flib.xdstorage.search;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.IXdStorageWatcher;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.search.key.XdStorageSearchIndexKey;
import org.flib.xdstorage.search.key.XdStorageSearchIndexOperationType;
import org.flib.xdstorage.search.query.IXdStorageCriterion;
import org.flib.xdstorage.search.query.IXdStorageCriterionOperator;
import org.flib.xdstorage.search.query.IXdStoragePrimaryCriterion;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class XdStorageSearchManager implements IXdStorageSearchManager {

    protected final XdStorageServicesLocator services;

    public XdStorageSearchManager(final XdStorageServicesLocator provider) {
        this.services = provider;
    }

    @Override
    public boolean hasIndex(final Object object) {
        return !XdStorageObjectUtils.getClassInfo(object.getClass()).getIndexes().isEmpty();
    }

    protected XdStorageSearchIndexRecord buildIndexRecord(final XdStorageSearchIndex index, final Object object,
                                                          final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        final XdStorageObjectIdField idField = index.getIdField();
        final Object id = idField.get(object);

        final XdStorageSearchIndexRecordConfermer confermer = new XdStorageSearchIndexRecordConfermer();
        IXdStorageIdObservableWrapper recordWrapper = null;
        if(id == null) {
            confermer.registerObjectWithoutIdentifier(object);
            recordWrapper = createRecordWrapper(object, transaction, record, idField, confermer);
        } else {
            setIdentifyableInformation(record, id, object, idField, transaction);
        }

        final XdStorageObjectField primaryField = index.getPrimaryField();
        record.setPrimaryIndexValue(primaryField.get(object));

        for (final Map.Entry<String, XdStorageObjectField> entry : index.getFieldAccesors().entrySet()) {
            record.setFieldValue(entry.getKey(), entry.getValue().get(object));
        }
        final Collection<List<XdStoragePair<String, XdStorageObjectField>>> childsByProperties = index.getChildAccessors();
        for (final List<XdStoragePair<String, XdStorageObjectField>> childAccessors : childsByProperties) {
            for (final XdStoragePair<String, XdStorageObjectField> childProperty : childAccessors) {
                final String propertyName = childProperty.a;
                final Object propertyValue = childProperty.b.get(object);
                if (propertyValue == null) {
                    continue;
                }

                final Class<?> propertyClass = XdStorageObjectUtils.getEntityClass(propertyValue.getClass());
                if (propertyClass.isArray()) {
                    final Class<?> type = XdStorageObjectUtils.getEntityClass(propertyClass.getComponentType());
                    recordWrapper = addArrayObjectsPropertiesToIndex(propertyName, index, record, type, (Object[]) propertyValue,
                            recordWrapper, confermer, object, idField, transaction);
                } else if (propertyValue instanceof Collection<?>) {
                    recordWrapper = addCollectionObjectsPropertiesToIndex(propertyName, index, record, (Collection<?>) propertyValue,
                            recordWrapper, confermer, object, idField, transaction);
                } else if (propertyValue instanceof Map<?, ?>) {
                    recordWrapper = addMapObjectsPropertiesToIndex(propertyName, index, record, (Map<?, ?>) propertyValue,
                            recordWrapper, confermer, object, idField, transaction);
                } else {
                    recordWrapper = addObjectPropertiesToIndex(propertyName, index, record, propertyClass, propertyValue,
                            recordWrapper, confermer, object, idField, transaction);
                }
            }
        }

        return recordWrapper != null ? (XdStorageSearchIndexRecord) recordWrapper : record;
    }

    private IXdStorageIdObservableWrapper createRecordWrapper(final Object object, final XdStorageTransaction transaction,
                                                              final XdStorageSearchIndexRecord record, final XdStorageObjectIdField idField,
                                                              final XdStorageSearchIndexRecordConfermer confermer) {
        IXdStorageIdObservableWrapper recordWrapper;
        recordWrapper = XdStorageObserverService.getObservableWrapper(record);

        if (idField.get(object) == null) {
            final IXdStorageIdObservableWrapper objectWrapper = XdStorageObserverService.getObservableWrapper(object);
            final IXdStorageIdObservableWrapper finalRecordWrapper = recordWrapper;
            objectWrapper.addObserver(new XdStorageAbstractIdObserver() {
                @Override
                public void onNewIdIsSet(final IXdStorageIdObservableWrapper wrapper, final Object id) {
                    confermer.unregisterObjectWithoutIdentifier(XdStorageObjectUtils.getWrappedObjectOrSameObject(wrapper));
                    if (confermer.noObjectsWithoutIdentifier()) {
                        final XdStorageSearchIndexRecord wrappedRecord = (XdStorageSearchIndexRecord) finalRecordWrapper;
                        try {
                            setIdentifyableInformation(wrappedRecord, id, object, idField, transaction);
                        } catch (final XdStorageException | XdStorageConnectionException e) {
                            throw new XdStorageRuntimeException(e);
                        }
                    } else {
                        confermer.setIdForRootObject(id);
                    }
                }
            });
        }
        return recordWrapper;
    }

    private void setIdentifyableInformation(final XdStorageSearchIndexRecord record, final Object id,
                                            final Object object, final XdStorageObjectIdField idField,
                                            final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexRecord wrappedRecord = XdStorageObjectUtils.getWrappedObjectOrSameObject(record);
        final Object resourceId = getResourceId(id, object, idField, transaction);
        wrappedRecord.setResourceId(resourceId);
        record.setId(id);
    }

    private IXdStorageIdObservableWrapper addCollectionObjectsPropertiesToIndex(final String childFieldName, final XdStorageSearchIndex index,
                                                                                final XdStorageSearchIndexRecord record, final Collection<?> collection,
                                                                                final IXdStorageIdObservableWrapper wrapper, final XdStorageSearchIndexRecordConfermer confermer,
                                                                                final Object rootObject, final XdStorageObjectIdField rootObjectIdField,
                                                                                final XdStorageTransaction transaction) {
        IXdStorageIdObservableWrapper recordWrapper = wrapper;
        for (final Object object : collection) {
            final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());

            final XdStorageObjectIdField idField = index.getChildIdField(cl);
            final Map<String, XdStorageObjectField> accessors = index.getChildFieldAccesors(cl);

            final Object objectId = idField.get(object);
            if(objectId == null) {
                confermer.registerObjectWithoutIdentifier(object);

                if(recordWrapper == null) {
                    recordWrapper = createRecordWrapper(rootObject, transaction, record, rootObjectIdField, confermer);
                }

                createChildWrapper(childFieldName, record, confermer, transaction, object, cl, rootObject, rootObjectIdField, accessors);
            } else {
                setChildInformation(childFieldName, record, object, cl, accessors, objectId);
            }
        }

        return recordWrapper;
    }



    private IXdStorageIdObservableWrapper addMapObjectsPropertiesToIndex(final String childFieldName, final XdStorageSearchIndex index,
                                                                         final XdStorageSearchIndexRecord record, final Map<?, ?> map,
                                                                         final IXdStorageIdObservableWrapper wrapper, final XdStorageSearchIndexRecordConfermer confermer,
                                                                         final Object rootObject, final XdStorageObjectIdField rootObjectIdField,
                                                                         final XdStorageTransaction transaction) {
        IXdStorageIdObservableWrapper recordWrapper = wrapper;
        for (final Object object : map.values()) {
            final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());

            final XdStorageObjectIdField idField = index.getChildIdField(cl);
            final Map<String, XdStorageObjectField> accessors = index.getChildFieldAccesors(cl);

            final Object objectId = idField.get(object);
            if(objectId == null) {
                confermer.registerObjectWithoutIdentifier(object);

                if(recordWrapper == null) {
                    recordWrapper = createRecordWrapper(rootObject, transaction, record, rootObjectIdField, confermer);
                }

                createChildWrapper(childFieldName, record, confermer, transaction, object, cl, rootObject, rootObjectIdField, accessors);
            } else {
                setChildInformation(childFieldName, record, object, cl, accessors, objectId);
            }
        }

        return recordWrapper;
    }

    private IXdStorageIdObservableWrapper addArrayObjectsPropertiesToIndex(final String childFieldName, final XdStorageSearchIndex index,
                                                                           final XdStorageSearchIndexRecord record, final Class<?> cl, final Object[] arr,
                                                                           final IXdStorageIdObservableWrapper wrapper, final XdStorageSearchIndexRecordConfermer confermer,
                                                                           final Object rootObject, final XdStorageObjectIdField rootObjectIdField,
                                                                           final XdStorageTransaction transaction) {
        IXdStorageIdObservableWrapper recordWrapper = wrapper;

        final XdStorageObjectIdField idField = index.getChildIdField(cl);
        final Map<String, XdStorageObjectField> accessors = index.getChildFieldAccesors(cl);
        for (final Object object : arr) {
            final Object objectId = idField.get(object);
            if(objectId == null) {
                confermer.registerObjectWithoutIdentifier(object);

                if(recordWrapper == null) {
                    recordWrapper = createRecordWrapper(rootObject, transaction, record, rootObjectIdField, confermer);
                }

                createChildWrapper(childFieldName, record, confermer, transaction, object, cl, rootObject, rootObjectIdField, accessors);
            } else {
                setChildInformation(childFieldName, record, object, cl, accessors, objectId);
            }
        }

        return recordWrapper;
    }

    private IXdStorageIdObservableWrapper addObjectPropertiesToIndex(final String childFieldName, final XdStorageSearchIndex index,
                                                                     final XdStorageSearchIndexRecord record, final Class<?> cl, final Object object,
                                                                     final IXdStorageIdObservableWrapper wrapper, final XdStorageSearchIndexRecordConfermer confermer,
                                                                     final Object rootObject, final XdStorageObjectIdField rootObjectIdField,
                                                                     final XdStorageTransaction transaction) {
        IXdStorageIdObservableWrapper recordWrapper = wrapper;

        final XdStorageObjectIdField idField = index.getChildIdField(cl);
        final Object objectId = idField.get(object);

        final Map<String, XdStorageObjectField> accessors = index.getChildFieldAccesors(cl);
        if(objectId == null) {
            confermer.registerObjectWithoutIdentifier(object);

            if(recordWrapper == null) {
                recordWrapper = createRecordWrapper(rootObject, transaction, record, rootObjectIdField, confermer);
            }

            createChildWrapper(childFieldName, record, confermer, transaction, object, cl, rootObject, rootObjectIdField, accessors);
        } else {
            setChildInformation(childFieldName, record, object, cl, accessors, objectId);
        }

        return recordWrapper;
    }

    private Object getResourceId(final Object objectId, final Object object, final XdStorageObjectIdField idField,
                                 final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = object.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();

        Object resourceId = null;
        if (policy == XdStoragePolicy.StoreAsClassObjects || policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageIndexDaoResource resource = services.getResourcesManager().lockIndexResource(clInfo, transaction);
            resourceId = resource.getObjectResourceId(objectId, transaction);
        }
        return resourceId;
    }

    private void createChildWrapper(final String childFieldName, final XdStorageSearchIndexRecord record,
                                    final XdStorageSearchIndexRecordConfermer confermer, final XdStorageTransaction transaction,
                                    final Object object, final Class<?> cl, final Object rootObject, final XdStorageObjectIdField rootObjectIdField,
                                    final Map<String, XdStorageObjectField> accessors) {
        final IXdStorageIdObservableWrapper objectWrapper = XdStorageObserverService.getObservableWrapper(object);
        objectWrapper.addObserver(new XdStorageAbstractIdObserver() {
            @Override
            public void onNewIdIsSet(final IXdStorageIdObservableWrapper wrapper, final Object id) {
                confermer.unregisterObjectWithoutIdentifier(XdStorageObjectUtils.getWrappedObjectOrSameObject(wrapper));

                setChildInformation(childFieldName, record, object, cl, accessors, id);

                if(confermer.noObjectsWithoutIdentifier()) {
                    final XdStorageSearchIndexRecord wrappedRecord = (XdStorageSearchIndexRecord) XdStorageObserverService.getObservableWrapper(record);
                    try {
                        final Object objectId = confermer.getIdForRootObject() != null ? confermer.getIdForRootObject() : wrappedRecord.getId();
                        setIdentifyableInformation(wrappedRecord, objectId, rootObject, rootObjectIdField, transaction);
                    } catch (final XdStorageException | XdStorageConnectionException e) {
                        throw new XdStorageRuntimeException(e);
                    }
                }
            }
        });
    }

    private void setChildInformation(final String childFieldName, final XdStorageSearchIndexRecord record,
                                     final Object object, final Class<?> cl, final Map<String, XdStorageObjectField> accessors,
                                     final Object objectId) {
        record.addChildIdIfNotExists(childFieldName, cl, objectId);

        for (final Map.Entry<String, XdStorageObjectField> accessor : accessors.entrySet()) {
            record.addChildFieldValue(childFieldName, cl, objectId, accessor.getKey(), accessor.getValue().get(object));
        }
    }

    @Override
    public void insert(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = object.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final Map<String, XdStorageSearchIndex> indexes = clInfo.getIndexes();

        insert(cl, indexes, object, transaction);
    }

    private void insert(final Class<?> cl, final Map<String, XdStorageSearchIndex> indexes, final Object object,
                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        for (XdStorageSearchIndex index : indexes.values()) {
            final IXdStorageSearchIndexDaoResource searchIndexResource = services.getResourcesManager().lockSearchIndexResource(clInfo, index.getName(), transaction);
            searchIndexResource.insert(buildIndexRecord(index, object, transaction), transaction);
        }
    }

    @Override
    public void insert(final Collection<?> objects, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = objects.iterator().next().getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final Map<String, XdStorageSearchIndex> indexes = clInfo.getIndexes();

        for (final Object object : objects) {
            insert(cl, indexes, object, transaction);
        }
    }

    @Override
    public void update(final Object object, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = object.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final Map<String, XdStorageSearchIndex> indexes = clInfo.getIndexes();

        for (XdStorageSearchIndex index : indexes.values()) {
            final IXdStorageSearchIndexDaoResource searchIndexResource = services.getResourcesManager().lockSearchIndexResource(clInfo, index.getName(), transaction);
            searchIndexResource.update(buildIndexRecord(index, object, transaction), transaction);
        }
    }

    @Override
    public void delete(final Object reference, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = reference.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final Map<String, XdStorageSearchIndex> indexes = clInfo.getIndexes();

        delete(cl, indexes, reference, transaction);
    }

    private void delete(final Class<?> cl, final Map<String, XdStorageSearchIndex> indexes, final Object reference,
                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        for (XdStorageSearchIndex index : indexes.values()) {
            final IXdStorageSearchIndexDaoResource searchIndexResource = services.getResourcesManager().lockSearchIndexResource(clInfo, index.getName(), transaction);
            searchIndexResource.delete(buildIndexRecord(index, reference, transaction), transaction);
        }
    }

    @Override
    public void delete(final Collection<?> references, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = references.iterator().next().getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final Map<String, XdStorageSearchIndex> indexes = clInfo.getIndexes();

        for (final Object reference : references) {
            delete(cl, indexes, reference, transaction);
        }
    }

    @Override
    public <T> Collection<T> search(final Class<T> cl, final String indexName, final XdStorageSearchQuery query,
                                    final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStorageSearchIndex index = clInfo.getIndexes().get(indexName);
        if (index == null) {
            throw new XdStorageException("requred index '" + indexName + "' for class " + cl.getName() + " doesn't exist");
        }

        final XdStoragePolicy policy = clInfo.getPolicy();

        final Map<Object, T> selection = new HashMap<>();

        final IXdStorage storage = services.getStorage();

        final XdStorageBTreeId searchBTreeId = new XdStorageBTreeId(clInfo.getClazz(), indexName);

        final XdStorageBTree tree = storage.load(XdStorageBTree.class, searchBTreeId, transaction);
        if (tree != null) {
            handleQuery(query, transaction, clInfo, index, policy, selection, storage, tree);
        }

        final List<T> result = new ArrayList<>(selection.size());
        result.addAll(selection.values());
        return result;
    }

    private <T> void handleQuery(final XdStorageSearchQuery query, final XdStorageTransaction transaction, final XdStorageClassInfo clInfo,
                                 final XdStorageSearchIndex index, final XdStoragePolicy policy, final Map<Object, T> result,
                                 final IXdStorage storage, final XdStorageBTree tree) throws XdStorageException, XdStorageConnectionException {
        final List<XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion>> notEquals = Collections.synchronizedList(new ArrayList<>());

        final AtomicBoolean failed = new AtomicBoolean(false);
        final XdStorageException[] ex = new XdStorageException[] { null };

        query.getCriterions().parallelStream().forEach(pair -> {
            if (failed.get()) {
                return;
            }

            if (pair.a != null && pair.a.getOperator() == IXdStorageCriterionOperator.EQUALS) {
                try {
                    handleEqualsPrimaryCriterion(transaction, clInfo, policy, result, storage, tree, pair.a, pair.b);
                } catch (final XdStorageException e) {
                    failed.set(true);
                    ex[0] = e;
                } catch (final XdStorageConnectionException e) {
                    failed.set(true);
                    ex[0] = new XdStorageException(e);
                }
            } else {
                notEquals.add(pair);
            }
        });

        if (ex[0] != null) {
            throw ex[0];
        }

        if (!notEquals.isEmpty()) {
            handleNotEqualsPrimaryCriterions(transaction, clInfo, index, policy, result, notEquals);
            return;
        }
    }

    private <T> void handleNotEqualsPrimaryCriterions(final XdStorageTransaction transaction, final XdStorageClassInfo clInfo,
                                                      final XdStorageSearchIndex index, final XdStoragePolicy policy,
                                                      final Map<Object, T> result, final List<XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion>> notEquals)
            throws XdStorageException, XdStorageConnectionException {
        final IXdStorageSearchIndexDaoResource resource = services.getResourcesManager().lockSearchIndexResource(clInfo, index.getName(), transaction);
        resource.watch(transaction, new IXdStorageWatcher<XdStorageSearchIndexRecord>() {
            @Override
            public void watch(final XdStorageSearchIndexRecord object) {
                for (final XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion> pair : notEquals) {
                    final IXdStoragePrimaryCriterion primaryCriterion = pair.a;
                    final IXdStorageCriterion criterion = pair.b;

                    if (primaryCriterion != null && !primaryCriterion.passed(object)) {
                        continue;
                    }
                    if (criterion == null || criterion.passed(object)) {
                        try {
                            final Object objectId = object.getId();
                            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                                final IXdStorageDaoResource resource = services.getResourcesManager().lockResource(object.getResourceId(), clInfo, transaction);
                                result.put(objectId, (T) resource.read(objectId, transaction));
                                break;
                            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                                final IXdStorageDaoResource resource = services.getResourcesManager().lockObjectResource(clInfo, objectId, transaction);
                                result.put(objectId, (T) resource.read(objectId, transaction));
                                break;
                            }
                        } catch (final XdStorageException | XdStorageConnectionException e) {
                            throw new XdStorageRuntimeException(e);
                        }
                    }
                }
            }
        });
    }

    private <T> void handleEqualsPrimaryCriterion(final XdStorageTransaction transaction, final XdStorageClassInfo clInfo,
                                                  final XdStoragePolicy policy, final Map<Object, T> result,
                                                  final IXdStorage storage, final XdStorageBTree tree,
                                                  final IXdStoragePrimaryCriterion primaryCriterion,
                                                  final IXdStorageCriterion criterion) throws XdStorageException, XdStorageConnectionException {
        final XdStorageSearchIndexKey key = new XdStorageSearchIndexKey(primaryCriterion.getValue(), null, XdStorageSearchIndexOperationType.Search);
        final List<Object> records = tree.find(key, storage, transaction);
        records.stream().forEach(rec -> {
            final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) rec;
            if (criterion == null || criterion.passed(record)) {
                final Object objectId = record.getId();
                try {
                    if (policy == XdStoragePolicy.StoreAsClassObjects) {
                        final IXdStorageDaoResource resource = services.getResourcesManager().lockResource(record.getResourceId(), clInfo, transaction);
                        result.put(objectId, (T) resource.read(objectId, transaction));
                    } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                        final IXdStorageDaoResource resource = services.getResourcesManager().lockObjectResource(clInfo, objectId, transaction);
                        result.put(objectId, (T) resource.read(objectId, transaction));
                    }
                } catch (final XdStorageException | XdStorageConnectionException e) {
                    transaction.markRollbackOnly();
                    throw new XdStorageRuntimeException(e);
                }
            }
        });
    }

    @Override
    public <T> Collection<T> search(final Class<T> cl, final String indexName, final XdStorageSqlSearchQuery query, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        throw new XdStorageException("Unsupported operation exception");
    }
}
