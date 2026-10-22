package io.github.xdstorage.search;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.IXdStorageWatcher;
import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.btree.XdStorageBTree;
import io.github.xdstorage.btree.XdStorageBTreeId;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.index.IXdStorageIndexDaoResource;
import io.github.xdstorage.observing.IXdStorageIdObservableWrapper;
import io.github.xdstorage.observing.XdStorageAbstractIdObserver;
import io.github.xdstorage.observing.XdStorageObserverService;
import io.github.xdstorage.resource.IXdStorageDaoResource;
import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;
import io.github.xdstorage.btree.IXdStorageBTreeViewer;
import io.github.xdstorage.search.key.XdStorageSearchIndexKey;
import io.github.xdstorage.search.key.XdStorageSearchIndexOperationType;
import io.github.xdstorage.search.query.IXdStorageCriterion;
import io.github.xdstorage.search.query.IXdStorageCriterionOperator;
import io.github.xdstorage.search.query.IXdStoragePrimaryCriterion;
import io.github.xdstorage.search.query.XdStorageRange;
import io.github.xdstorage.search.query.XdStorageSearchQuery;
import io.github.xdstorage.services.XdStorageServicesLocator;
import io.github.xdstorage.transaction.XdStorageTransaction;
import io.github.xdstorage.utils.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class XdStorageSearchManager implements IXdStorageSearchManager {

    private static final Logger log = LoggerFactory.getLogger(XdStorageSearchManager.class);

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

        // КАНОНИЧЕСКОЕ ИСПРАВЛЕНИЕ (покрыто интеграционными тестами): ресурс id-индекса существует
        // ТОЛЬКО для StoreAsClassObjects. Для StoreAsSingleObject lockIndexResource возвращает null
        // (NPE при сохранении любого индексируемого одиночного объекта); сам resourceId при политике
        // одиночного объекта не используется - чтение идет через lockObjectResource(clInfo, objectId).
        Object resourceId = null;
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
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
        if (object == null) return;

        final Class<?> cl = object.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        // ВНИМАНИЕ (исправление GraphPhantom/R-23): прежний skip "id уже установлен -> индекс
        // построен" был НЕВЕРЕН для DATABASE_GENERATOR: фасад присваивает ид в индексном ресурсе
        // ДО вызова search-insert, поэтому первичное индексирование пропускалось НАВСЕГДА,
        // а последующий update падал на отсутствующем key-дереве. Идемпотентность повторных
        // save() обеспечивает SearchIndexResource.insert (проверка обратной записи).
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
        if (objects == null || objects.isEmpty()) return;

        final Class<?> cl = objects.iterator().next().getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final Map<String, XdStorageSearchIndex> indexes = clInfo.getIndexes();

        // Синтаксически точный пакетный защитный барьер для коллекций планет
        final XdStorageObjectIdField idField = clInfo.getIdField();

        for (final Object object : objects) {
            if (object == null) continue;

            if (idField != null) {
                final Object objectId = idField.get(object);
                // Если планета уже была проиндексирована при сохранении вселенной - пропускаем избыточный накат
                if (objectId != null) {
                    continue;
                }
            }

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
        log.debug("search-index delete for {} ({} index(es)) by tx {}", cl.getName(), indexes.size(), transaction.getTransactionId());
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
        final List<XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion>> ranges = Collections.synchronizedList(new ArrayList<>());

        final AtomicBoolean failed = new AtomicBoolean(false);
        final XdStorageException[] ex = new XdStorageException[] { null };

        query.getCriterions().stream().forEach(pair -> {
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
            } else if (pair.a != null && isRangeOperator(pair.a.getOperator())) {
                // FP-1a: GT/GTE/LT/LTE/BETWEEN/IN — bounded ordered walk on the search tree
                ranges.add(pair);
            } else {
                notEquals.add(pair);
            }
        });

        if (ex[0] != null) {
            throw ex[0];
        }

        for (final XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion> pair : ranges) {
            handleRangePrimaryCriterion(transaction, clInfo, policy, result, storage, tree, pair.a, pair.b);
        }

        if (!notEquals.isEmpty()) {
            handleNotEqualsPrimaryCriterions(transaction, clInfo, index, policy, result, notEquals);
        }
    }

    /** FP-1a: the range-operator family routed to the ordered bounded walk. */
    private static boolean isRangeOperator(final IXdStorageCriterionOperator operator) {
        return operator == IXdStorageCriterionOperator.GT
                || operator == IXdStorageCriterionOperator.GTE
                || operator == IXdStorageCriterionOperator.LT
                || operator == IXdStorageCriterionOperator.LTE
                || operator == IXdStorageCriterionOperator.BETWEEN
                || operator == IXdStorageCriterionOperator.IN;
    }

    /**
     * FP-1a: evaluates one range-operator primary criterion as a bounded ordered walk of the
     * search tree (early termination at the upper bound; IN as a single membership-filtered
     * walk). Result semantics are identical to the scan path — the operator itself
     * ({@code primaryCriterion.passed(record)}) is the semantic authority per record, the
     * bounds only prune; nulls never satisfy, mixed-type bounds fail loudly.
     */
    private <T> void handleRangePrimaryCriterion(final XdStorageTransaction transaction, final XdStorageClassInfo clInfo,
                                                 final XdStoragePolicy policy, final Map<Object, T> result,
                                                 final IXdStorage storage, final XdStorageBTree tree,
                                                 final IXdStoragePrimaryCriterion primaryCriterion,
                                                 final IXdStorageCriterion criterion) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageCriterionOperator operator = primaryCriterion.getOperator();
        final Object value = primaryCriterion.getValue();
        Comparable fromKey = null;
        boolean fromInclusive = false;
        Comparable toKey = null;
        boolean toInclusive = false;

        if (operator == IXdStorageCriterionOperator.GT) {
            fromKey = searchBoundKey(value, operator);
        } else if (operator == IXdStorageCriterionOperator.GTE) {
            fromKey = searchBoundKey(value, operator);
            fromInclusive = true;
        } else if (operator == IXdStorageCriterionOperator.LT) {
            toKey = searchBoundKey(value, operator);
        } else if (operator == IXdStorageCriterionOperator.LTE) {
            toKey = searchBoundKey(value, operator);
            toInclusive = true;
        } else if (operator == IXdStorageCriterionOperator.BETWEEN) {
            if (!(value instanceof XdStorageRange)) {
                throw new XdStorageRuntimeException("BETWEEN requires an " + XdStorageRange.class.getSimpleName()
                        + " criterion value, got " + (value == null ? "null" : value.getClass().getName()));
            }
            final XdStorageRange range = (XdStorageRange) value;
            fromKey = searchBoundKey(range.getFrom(), operator);
            fromInclusive = true;
            toKey = searchBoundKey(range.getTo(), operator);
            toInclusive = true;
        } else if (operator == IXdStorageCriterionOperator.IN) {
            // unbounded single walk; membership is decided per record by the operator
        } else {
            throw new XdStorageRuntimeException("not a range operator: " + operator);
        }

        try {
            tree.readRange(fromKey, fromInclusive, toKey, toInclusive, storage, transaction, new IXdStorageBTreeViewer() {
                @Override
                public void look(final Comparable key, final Object rec) throws XdStorageException, XdStorageConnectionException {
                    final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) rec;
                    if (!primaryCriterion.passed(record)) {
                        return;
                    }
                    if (criterion == null || criterion.passed(record)) {
                        fetchIntoResult(policy, clInfo, result, record, transaction);
                    }
                }
            });
        } catch (final ClassCastException e) {
            // the walk compares index entries with the wrapped bounds — a bound whose type
            // differs from the indexed values fails loudly here, never silently-empty
            throw new XdStorageRuntimeException("range operator " + operator + ": criterion bound of type "
                    + (value == null ? "null" : value.getClass().getName())
                    + " is not comparable with the indexed values of index criteria for class "
                    + clInfo.getClazz().getName() + " — mixed-type comparison fails loudly", e);
        }
    }

    private static Comparable searchBoundKey(final Object bound, final IXdStorageCriterionOperator operator) {
        if (bound == null) {
            throw new XdStorageRuntimeException("range operator " + operator
                    + " requires a non-null criterion bound — IS_NULL / IS_NOT_NULL are the null tests");
        }
        return new XdStorageSearchIndexKey(bound, null, XdStorageSearchIndexOperationType.Search);
    }

    /**
     * Shared tail of the index-driven fast paths: fetch the object behind an index record.
     * A record whose object is not visible in THIS transaction's snapshot (a concurrent commit
     * landed after the tx began — the index tree is live, the data read is snapshot-filtered)
     * contributes nothing rather than a null element: the result set stays snapshot-consistent.
     */
    @SuppressWarnings("unchecked")
    private <T> void fetchIntoResult(final XdStoragePolicy policy, final XdStorageClassInfo clInfo,
                                     final Map<Object, T> result, final XdStorageSearchIndexRecord record,
                                     final XdStorageTransaction transaction) throws XdStorageException {
        final Object objectId = record.getId();
        try {
            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                final IXdStorageDaoResource resource = services.getResourcesManager().lockResource(record.getResourceId(), clInfo, transaction);
                final Object object = resource.read(objectId, transaction);
                if (object != null) {
                    result.put(objectId, (T) object);
                }
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                final IXdStorageDaoResource resource = services.getResourcesManager().lockObjectResource(clInfo, objectId, transaction);
                final Object object = resource.read(objectId, transaction);
                if (object != null) {
                    result.put(objectId, (T) object);
                }
            }
        } catch (final XdStorageException | XdStorageConnectionException e) {
            transaction.markRollbackOnly();
            throw new XdStorageRuntimeException(e);
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
}
