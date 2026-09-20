package org.flib.xdstorage.sqlstorage.search;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.search.IXdStorageSearchIndexDaoResource;
import org.flib.xdstorage.search.XdStorageSearchIndexRecordConfermer;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.search.XdStorageSearchManager;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class XdStorageSQLSearchManager extends XdStorageSearchManager {

    public XdStorageSQLSearchManager(final XdStorageServicesLocator provider) {
        super(provider);
    }

    @Override
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

        Object resourceId;
        if (policy == XdStoragePolicy.StoreAsClassObjects || policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageIndexDaoResource resource = services.getResourcesManager().lockIndexResource(clInfo, transaction);
            resourceId = resource.getObjectResourceId(objectId, transaction);
        } else {
            final IXdStorageIndexDaoResource indexResource = services.getResourcesManager().lockIndexResource(clInfo, transaction);
            final Object objectResourceId = indexResource.getObjectResourceId(objectId, transaction);

            final IXdStorageDaoResource resource = ((XdStorageSQLResourcesManager)services.getResourcesManager()).lockChildrenClassResource(clInfo, objectResourceId, transaction);
            resourceId = resource.getResourceId();
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
    public <T> Collection<T> search(final Class<T> cl, final String indexName, final XdStorageSearchQuery query,
                                    final XdStorageTransaction transaction) throws XdStorageException {
        throw new XdStorageException("Unsupported operation exception");
    }

    @Override
    public <T> Collection<T> search(final Class<T> cl, final String indexName, final XdStorageSqlSearchQuery query,
                                    final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStorageSearchIndex index = clInfo.getIndexes().get(indexName);
        if (index == null) {
            throw new XdStorageException("requred index '" + indexName + "' for class " + cl.getName() + " doesn't exist");
        }

        final IXdStorageSearchIndexDaoResource searchIndexResource = services.getResourcesManager().lockSearchIndexResource(clInfo, indexName, transaction);

        final List<IXdStorageSimpleWrapper> references = searchIndexResource.selectObjectReferences(clInfo, query, transaction);

        final XdStoragePolicy policy = clInfo.getPolicy();
        for (IXdStorageSimpleWrapper reference : references) {
            if (reference.isReference__()) {
                if (policy == XdStoragePolicy.StoreAsClassObjects || policy == XdStoragePolicy.StoreAsSingleObject) {
                    final IXdStorageIndexDaoResource resource = services.getResourcesManager().lockIndexResource(clInfo, transaction);
                    resource.readByReference(reference, transaction);
                } else {
                    final IXdStorageIndexDaoResource indexResource = services.getResourcesManager().lockIndexResource(clInfo, transaction);
                    final Object objectResourceId = indexResource.getObjectResourceId(reference.getObjectId__(), transaction);

                    final IXdStorageDaoResource resource = ((XdStorageSQLResourcesManager) services.getResourcesManager()).lockChildrenClassResource(clInfo, objectResourceId, transaction);
                    resource.readByReference(reference, transaction);
                }
            }
        };

        return (Collection<T>) references;
    }
}
