package org.flib.xdstorage.sqlstorage.resource.helpers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.sqlstorage.index.XdStorageSQLHashIndexResource;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.*;

import java.lang.reflect.Array;
import java.util.*;

public class XdStorageResourceFirstPhaseInternalObjectsHelper {

    private static final Logger log = LogManager.getLogger(XdStorageResourceFirstPhaseInternalObjectsHelper.class);

    public static void collectChildrenObjectsAndLinks(final XdStorageTransaction transaction, final XdStorageSQLResourceId resourceId,
                                                      final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToInsert,
                                                      final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToDelete,
                                                      final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId,
                                                      final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate,
                                                      final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> objectsToInsertWithoutIdField,
                                                      final XdStorageTransactionResourceChanges record,
                                                      final XdStorageSQLResourcesManager manager)
            throws XdStorageException, XdStorageConnectionException {

        final IXdStorageSQLTypesHelper helper = manager.getTypesHelper();

        // [owner_id, collection_of_children_of_different_classes]
        final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> internalToInsert = new HashMap<>();
        final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> internalToUpdate = new HashMap<>();
        final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> internalToDelete = new HashMap<>();
        for (final XdStorageObjectChange change : record.getChangesObjects()) {
            collectInternalObjectsAndLinks(fkToInsert, fkToDelete, linksToInsertWithoutId, linksToUpdate, objectsToInsertWithoutIdField,
                    internalToInsert, internalToUpdate, internalToDelete, change.oldObject, change.newObject, helper);
        }

        registerChildrenObjectsToInsert(transaction, resourceId, internalToInsert, manager);
        registerChildrenObjectsToUpdate(transaction, resourceId, internalToUpdate, manager);
        registerChildrenObjectsToDelete(transaction, resourceId, internalToDelete, manager);
    }

    private static void registerChildrenObjectsToInsert(final XdStorageTransaction transaction, final XdStorageSQLResourceId resourceId,
                                                        final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> internalToInsert,
                                                        final XdStorageSQLResourcesManager manager)
            throws XdStorageException, XdStorageConnectionException {

        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> entry : internalToInsert.entrySet()) {

            for (final XdStoragePair<XdStorageObjectField, Object> pair : entry.getValue()) {
                final XdStorageObjectField field = pair.a;
                final Object value = pair.b;

                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();
                final Class<?> propertyClass = fieldInfo.getValueClass();

                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
                if (clInfo.getPolicy() != XdStoragePolicy.StoreWithParentObject) {
                    continue;
                }

                final XdStorageSQLHashIndexResource indexResource = (XdStorageSQLHashIndexResource)manager.lockIndexResource(clInfo, transaction);

                if (fieldInfo.isArray()) {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            indexResource.insertChild(entry.getKey(), object, resourceId, transaction);
                        }
                    } else {
                        final Object[] array = (Object[]) value;
                        for (final Object object : array) {
                            indexResource.insertChild(entry.getKey(), object, resourceId, transaction);
                        }
                    }
                } else if (fieldInfo.isCollection()) {
                    for (final Object object : (Collection<?>) value) {
                        indexResource.insertChild(entry.getKey(), object, resourceId, transaction);
                    }
                } else if (fieldInfo.isMap()) {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            indexResource.insertChild(entry.getKey(), object, resourceId, transaction);
                        }
                    } else {
                        for (final Object object : ((Map<?, ?>) value).values()) {
                            indexResource.insertChild(entry.getKey(), object, resourceId, transaction);
                        }
                    }
                } else {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            indexResource.insertChild(entry.getKey(), object, resourceId, transaction);
                        }
                    } else {
                        indexResource.insertChild(entry.getKey(), value, resourceId, transaction);
                    }
                }
            }
        }
    }

    private static void registerChildrenObjectsToUpdate(final XdStorageTransaction transaction, final XdStorageSQLResourceId resourceId,
                                                        final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> internalToUpdate,
                                                        final XdStorageSQLResourcesManager manager)
            throws XdStorageException, XdStorageConnectionException {

        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> entry : internalToUpdate.entrySet()) {

            for (final XdStoragePair<XdStorageObjectField, Object> pair : entry.getValue()) {
                final XdStorageObjectField field = pair.a;
                final Object value = pair.b;

                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

                final Class<?> propertyClass = fieldInfo.getValueClass();

                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
                if (clInfo.getPolicy() != XdStoragePolicy.StoreWithParentObject) {
                    continue;
                }

                final IXdStorageDaoResource resource = manager.lockChildrenClassResource(clInfo, resourceId, transaction);

                if (fieldInfo.isArray()) {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            resource.update(object, transaction);
                        }
                    } else {
                        final int length = java.lang.reflect.Array.getLength(value);
                        final Object[] array = (Object[]) value;
                        for (final Object object : array) {
                            resource.update(object, transaction);
                        }
                    }
                } else if (fieldInfo.isCollection()) {
                    for (final Object object : (Collection<?>) value) {
                        resource.update(object, transaction);
                    }
                } else if (fieldInfo.isMap()) {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            resource.update(object, transaction);
                        }
                    } else {
                        for (final Object object : ((Map<?, ?>) value).values()) {
                            resource.update(object, transaction);
                        }
                    }
                } else {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            resource.update(object, transaction);
                        }
                    } else {
                        resource.update(value, transaction);
                    }
                }
            }
        }
    }

    private static void registerChildrenObjectsToDelete(final XdStorageTransaction transaction, final XdStorageSQLResourceId resourceId,
                                                        final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> internalToDelete,
                                                        final XdStorageSQLResourcesManager manager)
            throws XdStorageException, XdStorageConnectionException {

        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> entry : internalToDelete.entrySet()) {

            for (final XdStoragePair<XdStorageObjectField, Object> pair : entry.getValue()) {
                final XdStorageObjectField field = pair.a;
                final Object value = pair.b;

                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

                final Class<?> propertyClass = fieldInfo.getValueClass();

                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
                final XdStorageSQLHashIndexResource indexResource = (XdStorageSQLHashIndexResource)manager.lockIndexResource(clInfo, transaction);

                if (fieldInfo.isArray()) {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            deleteChild(clInfo.getPolicy(), entry.getKey(), object, resourceId, indexResource, transaction);
                        }
                    } else {
                        final int length = java.lang.reflect.Array.getLength(value);
                        final Object[] array = (Object[]) value;
                        for (final Object object : array) {
                            deleteChild(clInfo.getPolicy(), entry.getKey(), object, resourceId, indexResource, transaction);
                        }
                    }
                } else if (fieldInfo.isCollection()) {
                    for (final Object object : (Collection<?>) value) {
                        deleteChild(clInfo.getPolicy(), entry.getKey(), object, resourceId, indexResource, transaction);
                    }
                } else if (fieldInfo.isMap()) {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            deleteChild(clInfo.getPolicy(), entry.getKey(), object, resourceId, indexResource, transaction);
                        }
                    } else {
                        for (final Object object : ((Map<?, ?>) value).values()) {
                            deleteChild(clInfo.getPolicy(), entry.getKey(), object, resourceId, indexResource, transaction);
                        }
                    }
                } else {
                    if (Collection.class.isAssignableFrom(value.getClass())) {
                        for (final Object object : (Collection<?>) value) {
                            deleteChild(clInfo.getPolicy(), entry.getKey(), object, resourceId, indexResource, transaction);
                        }
                    } else {
                        deleteChild(clInfo.getPolicy(), entry.getKey(), value, resourceId, indexResource, transaction);
                    }
                }
            }
        }
    }

    private static void deleteChild(final XdStoragePolicy policy, final Object parentObject, final Object childObject, final XdStorageSQLResourceId parentObjectResourceId,
                             final XdStorageSQLHashIndexResource indexResource, final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {

        if (policy == XdStoragePolicy.StoreWithParentObject) {
            indexResource.deleteChild(parentObject, childObject, parentObjectResourceId, transaction);
        } else {
            indexResource.deleteForeignKey(parentObject, childObject, parentObjectResourceId, transaction);
        }
    }

    private static void collectInternalObjectsAndLinks(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToInsert,
                                                       final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToDelete,
                                                       final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId,
                                                       final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate,
                                                       final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> objectsToInsertWithoutIdField,
                                                       final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                                       final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                                       final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                                       final Object oldObject, final Object newObject, final IXdStorageSQLTypesHelper helper) {
        final XdStorageClassInfo clInfo;
        if (newObject != null) {
            clInfo = XdStorageObjectUtils.getClassInfo(newObject.getClass());
        } else {
            clInfo = XdStorageObjectUtils.getClassInfo(oldObject.getClass());
        }
        if (oldObject == null) {
            final Object referenceKey = newObject;
            for (final XdStorageObjectField field : clInfo.getFields().values()) {
                if (helper.isSimpleType(field)) {
                    continue;
                }

                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

                final Class<?> propertyClass = fieldInfo.getValueClass();

                final XdStorageClassInfo internalClassInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
                final XdStorageObjectIdField internalClassIdField = internalClassInfo.getIdField();

                final Object value = field.get(newObject);
                if (value != null) {
                    if (internalClassIdField != null) {
                        Collection<XdStoragePair<XdStorageObjectField, Object>> objects = toInsert.get(referenceKey);
                        if (objects == null) {
                            toInsert.put(referenceKey, objects = new ArrayList<>());
                        }
                        objects.add(new XdStoragePair<>(field, value));

                        Collection<XdStoragePair<XdStorageObjectField, Object[]>> linksToInsert = linksToInsertWithoutId.get(referenceKey);
                        if (linksToInsert == null) {
                            linksToInsertWithoutId.put(referenceKey, linksToInsert = new ArrayList<>());
                        }
                        Collection<XdStoragePair<XdStorageObjectField, Object[]>> links = linksToUpdate.get(referenceKey);
                        if (links == null) {
                            linksToUpdate.put(referenceKey, links = new ArrayList<>());
                        }
                        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToInsertList = fkToInsert.computeIfAbsent(referenceKey, key -> new ArrayList<>());
                        collectInternalObjectsLinks(fkToInsertList, linksToInsert, links, field, value);
                    } else {
                        collectInternalObjectsWithoutIdField(objectsToInsertWithoutIdField, newObject, field, value);
                    }
                }
            }
        } else if (newObject == null) {
            final Object referenceKey = oldObject;
            for (final XdStorageObjectField field : clInfo.getFields().values()) {
                if (helper.isSimpleType(field)) {
                    continue;
                }

                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

                final Class<?> propertyClass = fieldInfo.getValueClass();

                final XdStorageClassInfo internalClassInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
                final XdStorageObjectIdField internalClassIdField = internalClassInfo.getIdField();

                final Object value = field.get(oldObject);
                if (internalClassIdField != null) {
                    if (value != null) {
                        Collection<XdStoragePair<XdStorageObjectField, Object>> objects = toDelete.get(referenceKey);
                        if (objects == null) {
                            toDelete.put(referenceKey, objects = new ArrayList<>());
                        }
                        objects.add(new XdStoragePair<>(field, value));

                        Collection<XdStoragePair<XdStorageObjectField, Object[]>> links = linksToUpdate.get(referenceKey);
                        if (links == null) {
                            linksToUpdate.put(referenceKey, links = new ArrayList<>());
                        }
                        links.add(new XdStoragePair<>(field, null));

                        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToDeleteList = fkToDelete.computeIfAbsent(referenceKey, key -> new ArrayList<>());
                        collectInternalObjectsToDelete(fkToDeleteList, field, value);
                    }
                } else {
                    Collection<XdStoragePair<XdStorageObjectField, Object[]>> internalObjects = objectsToInsertWithoutIdField.get(referenceKey);
                    if (internalObjects == null) {
                        objectsToInsertWithoutIdField.put(referenceKey, internalObjects = new ArrayList<>());
                    }
                    internalObjects.add(new XdStoragePair<>(field, null));
                }
            }
        } else {
            final Object referenceKey = newObject;
            for (final XdStorageObjectField field : clInfo.getFields().values()) {
                if (helper.isSimpleType(field)) {
                    continue;
                }

                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

                final Class<?> propertyClass = fieldInfo.getValueClass();

                final XdStorageClassInfo internalClassInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
                final XdStorageObjectIdField internalClassIdField = internalClassInfo.getIdField();

                final Object oldValueOfField = field.get(oldObject);
                final Object newValueOfField = field.get(newObject);
                if (oldValueOfField == null) {
                    if (newValueOfField != null) {
                        // internal objects were inserted
                        if (internalClassIdField != null) {
                            Collection<XdStoragePair<XdStorageObjectField, Object>> objects = toInsert.get(referenceKey);
                            if (objects == null) {
                                toInsert.put(referenceKey, objects = new ArrayList<>());
                            }
                            objects.add(new XdStoragePair<>(field, newValueOfField));

                            Collection<XdStoragePair<XdStorageObjectField, Object[]>> linksToInsert = linksToInsertWithoutId.get(referenceKey);
                            if (linksToInsert == null) {
                                linksToInsertWithoutId.put(referenceKey, linksToInsert = new ArrayList<>());
                            }
                            Collection<XdStoragePair<XdStorageObjectField, Object[]>> links = linksToUpdate.get(referenceKey);
                            if (links == null) {
                                linksToUpdate.put(referenceKey, links = new ArrayList<>());
                            }
                            Collection<XdStoragePair<XdStorageObjectField, Object>> fkToInsertList = fkToInsert.computeIfAbsent(referenceKey, key -> new ArrayList<>());
                            collectInternalObjectsLinks(fkToInsertList, linksToInsert, links, field, newValueOfField);
                        } else {
                            collectInternalObjectsWithoutIdField(objectsToInsertWithoutIdField, newObject, field, newValueOfField);
                        }
                    }
                } else if (newValueOfField == null) {
                    // internal objects were deleted
                    if (internalClassIdField != null) {
                        Collection<XdStoragePair<XdStorageObjectField, Object>> objects = toDelete.get(referenceKey);
                        if (objects == null) {
                            toDelete.put(referenceKey, objects = new ArrayList<>());
                        }
                        objects.add(new XdStoragePair<>(field, field.get(oldObject)));

                        Collection<XdStoragePair<XdStorageObjectField, Object[]>> links = linksToUpdate.get(referenceKey);
                        if (links == null) {
                            linksToUpdate.put(referenceKey, links = new ArrayList<>());
                        }
                        links.add(new XdStoragePair<>(field, null));

                        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToDeleteList = fkToDelete.computeIfAbsent(referenceKey, key -> new ArrayList<>());
                        collectInternalObjectsToDelete(fkToDeleteList, field, field.get(oldObject));
                    } else {
                        Collection<XdStoragePair<XdStorageObjectField, Object[]>> internalObjects = objectsToInsertWithoutIdField.get(referenceKey);
                        if (internalObjects == null) {
                            objectsToInsertWithoutIdField.put(referenceKey, internalObjects = new ArrayList<>());
                        }
                        internalObjects.add(new XdStoragePair<>(field, null));
                    }
                } else {
                    if (internalClassIdField != null) {
                        distributeObjectsField(fkToInsert, fkToDelete, linksToInsertWithoutId, linksToUpdate,
                                toInsert, toUpdate, toDelete, referenceKey, field, oldValueOfField, newValueOfField);
                    } else {
                        collectInternalObjectsWithoutIdField(objectsToInsertWithoutIdField, newObject, field, newValueOfField);
                    }
                }
            }
        }
    }

    private static void collectInternalObjectsWithoutIdField(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> objectsToInsertWithoutIdField,
                                                             final Object parentObject, final XdStorageObjectField field, final Object value) {
        if (value == null) {
            return;
        }

        Collection<XdStoragePair<XdStorageObjectField, Object[]>> objects = objectsToInsertWithoutIdField.get(parentObject);
        if (objects == null) {
            objectsToInsertWithoutIdField.put(parentObject, objects = new ArrayList<>());
        }

        final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

        if (fieldInfo.isArray()) {
            final int length = Array.getLength(value);
            if (length == 0) {
                objects.add(new XdStoragePair<>(field, null));
            } else {
                boolean hasToInsert = false;
                final Object[] array = (Object[]) value;
                for (int i = 0; i < length; ++i) {
                    objects.add(new XdStoragePair<>(field, new Object[]{array[i]}));
                    hasToInsert = true;
                }
                if(!hasToInsert) {
                    objects.add(new XdStoragePair<>(field, null));
                }
            }
        } else if (fieldInfo.isCollection()) {
            boolean hasToInsert = false;
            for (final Object object : (Collection<?>) value) {
                objects.add(new XdStoragePair<>(field, new Object[]{object}));
                hasToInsert = true;
            }
            if (!hasToInsert) {
                objects.add(new XdStoragePair<>(field, null));
            }
        } else if (fieldInfo.isMap()) {
            boolean hasToInsert = false;
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                objects.add(new XdStoragePair<>(field, new Object[]{entry.getKey(), entry.getValue()}));
                hasToInsert = true;
            }
            if (!hasToInsert) {
                objects.add(new XdStoragePair<>(field, null));
            }
        } else {
            objects.add(new XdStoragePair<>(field, new Object[]{value}));
        }
    }

    private static void collectInternalObjectsLinks(final Collection<XdStoragePair<XdStorageObjectField, Object>> fkToInsert,
                                                    final Collection<XdStoragePair<XdStorageObjectField, Object[]>> linksToInsertWithoutId,
                                                    final Collection<XdStoragePair<XdStorageObjectField, Object[]>> linksToUpdate,
                                                    final XdStorageObjectField field, final Object value) {
        if (value == null) {
            return;
        }

        final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

        final Class<?> propertyClass = fieldInfo.getValueClass();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
        final XdStorageObjectIdField propertyIdField = clInfo.getIdField();

        if (fieldInfo.isArray()) {
            final int length = Array.getLength(value);
            if (length == 0) {
                linksToUpdate.add(new XdStoragePair<>(field, null));
            } else {
                boolean hasToUpdate = false;
                final Object[] array = (Object[]) value;
                for (int i = 0; i < length; ++i) {
                    final Object id = propertyIdField.get(array[i]);
                    if (id == null) {
                        linksToInsertWithoutId.add(new XdStoragePair<>(field, new Object[]{array[i]}));
                    } else {
                        fkToInsert.add(new XdStoragePair<>(field, array[i]));
                        linksToUpdate.add(new XdStoragePair<>(field, new Object[]{array[i]}));
                        hasToUpdate = true;
                    }
                }
                if (!hasToUpdate) {
                    linksToUpdate.add(new XdStoragePair<>(field, null));
                }
            }
        } else if (fieldInfo.isCollection()) {
            boolean hasToUpdate = false;
            for (final Object object : (Collection<?>) value) {
                final Object id = propertyIdField.get(object);
                if (id == null) {
                    linksToInsertWithoutId.add(new XdStoragePair<>(field, new Object[]{object}));
                } else {
                    fkToInsert.add(new XdStoragePair<>(field, object));
                    linksToUpdate.add(new XdStoragePair<>(field, new Object[]{object}));
                    hasToUpdate = true;
                }
            }
            if (!hasToUpdate) {
                linksToUpdate.add(new XdStoragePair<>(field, null));
            }
        } else if (fieldInfo.isMap()) {
            boolean hasToUpdate = false;
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                final Object id = propertyIdField.get(entry.getValue());
                if (id == null) {
                    linksToInsertWithoutId.add(new XdStoragePair<>(field, new Object[]{entry.getKey(), entry.getValue()}));
                } else {
                    fkToInsert.add(new XdStoragePair<>(field, entry.getValue()));
                    linksToUpdate.add(new XdStoragePair<>(field, new Object[]{entry.getKey(), entry.getValue()}));
                    hasToUpdate = true;
                }
            }
            if (!hasToUpdate) {
                linksToUpdate.add(new XdStoragePair<>(field, null));
            }
        } else {
            boolean hasToUpdate = false;
            final Object id = propertyIdField.get(value);
            if (id == null) {
                linksToInsertWithoutId.add(new XdStoragePair<>(field, new Object[]{value}));
            } else {
                fkToInsert.add(new XdStoragePair<>(field, value));
                linksToUpdate.add(new XdStoragePair<>(field, new Object[]{value}));
                hasToUpdate = true;
            }
            if (!hasToUpdate) {
                linksToUpdate.add(new XdStoragePair<>(field, null));
            }
        }
    }

    private static void collectInternalObjectsToDelete(final Collection<XdStoragePair<XdStorageObjectField, Object>> fkToDelete,
                                                       final XdStorageObjectField field, final Object value) {
        if (value == null) {
            return;
        }

        final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

        final Class<?> propertyClass = fieldInfo.getValueClass();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
        final XdStorageObjectIdField propertyIdField = clInfo.getIdField();

        if (fieldInfo.isArray()) {
            final int length = Array.getLength(value);
            if (length > 0) {
                final Object[] array = (Object[]) value;
                for (int i = 0; i < length; ++i) {
                    final Object id = propertyIdField.get(array[i]);
                    if (id != null) {
                        fkToDelete.add(new XdStoragePair<>(field, array[i]));
                    }
                }
            }
        } else if (fieldInfo.isCollection()) {
            for (final Object object : (Collection<?>) value) {
                final Object id = propertyIdField.get(object);
                if (id != null) {
                    fkToDelete.add(new XdStoragePair<>(field, object));
                }
            }
        } else if (fieldInfo.isMap()) {
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                final Object id = propertyIdField.get(entry.getValue());
                if (id != null) {
                    fkToDelete.add(new XdStoragePair<>(field, entry.getValue()));
                }
            }
        } else {
            final Object id = propertyIdField.get(value);
            if (id != null) {
                fkToDelete.add(new XdStoragePair<>(field, value));
            }
        }
    }

    private static void distributeObjectsField(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToInsert,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToDelete,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                               final Object referenceKey, final XdStorageObjectField field,
                                               final Object oldValueOfField, final Object newValueOfField) {
        final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

        final Class<?> propertyClass = fieldInfo.getValueClass();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
        final XdStorageObjectIdField propertyIdField = clInfo.getIdField();

        if (fieldInfo.isArray()) {
            destributeArrayObjectsField(fkToInsert, fkToDelete, linksToInsertWithoutId, linksToUpdate,
                    toInsert, toUpdate, toDelete, referenceKey, field, oldValueOfField, newValueOfField, propertyClass, propertyIdField);
        } else if (fieldInfo.isCollection()) {
            distributeCollectionObjectsField(fkToInsert, fkToDelete, linksToInsertWithoutId, linksToUpdate,
                    toInsert, toUpdate, toDelete, referenceKey, field, (Collection<?>) oldValueOfField, (Collection<?>) newValueOfField, propertyIdField);
        } else if (fieldInfo.isMap()) {
            distributeMapObjectsField(fkToInsert, fkToDelete, linksToInsertWithoutId, linksToUpdate,
                    toInsert, toUpdate, toDelete, referenceKey, field, (Map<?, ?>) oldValueOfField, (Map<?, ?>) newValueOfField, propertyIdField);
        } else {
            distributeObjectField(linksToInsertWithoutId, linksToUpdate,
                    toInsert, toUpdate, toDelete, referenceKey, field, oldValueOfField, newValueOfField, propertyIdField);
        }
    }

    private static void distributeObjectField(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId,
                                              final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate,
                                              final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                              final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                              final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                              final Object referenceKey, final XdStorageObjectField field,
                                              final Object oldValueOfField, final Object newValueOfField,
                                              final XdStorageObjectIdField propertyIdField) {
        final Collection<Object> toInsertObjects = new ArrayList<>();
        final Collection<Object> toUpdateObjects = new ArrayList<>();
        final Collection<Object> toDeleteObjects = new ArrayList<>();

        final Object idOfOld = propertyIdField.get(oldValueOfField);
        final Object idOfNew = propertyIdField.get(newValueOfField);

        if (idOfNew != null && idOfNew.equals(idOfOld)) {
            toUpdateObjects.add(newValueOfField);

            Collection<XdStoragePair<XdStorageObjectField, Object[]>> links = linksToUpdate.get(referenceKey);
            if (links == null) {
                linksToUpdate.put(referenceKey, links = new ArrayList<>());
            }
            links.add(new XdStoragePair<>(field, new Object[]{newValueOfField}));
        } else {
            toInsertObjects.add(newValueOfField);
            toDeleteObjects.add(oldValueOfField);

            if (idOfNew == null) {
                Collection<XdStoragePair<XdStorageObjectField, Object[]>> links = linksToInsertWithoutId.get(referenceKey);
                if (links == null) {
                    linksToInsertWithoutId.put(referenceKey, links = new ArrayList<>());
                }
                links.add(new XdStoragePair<>(field, new Object[]{newValueOfField}));

                links = linksToUpdate.get(referenceKey);
                if (links == null) {
                    linksToUpdate.put(referenceKey, links = new ArrayList<>());
                }
                links.add(new XdStoragePair<>(field, null));
            } else {
                Collection<XdStoragePair<XdStorageObjectField, Object[]>> links = linksToUpdate.get(referenceKey);
                if (links == null) {
                    linksToUpdate.put(referenceKey, links = new ArrayList<>());
                }
                links.add(new XdStoragePair<>(field, new Object[]{newValueOfField}));
            }
        }

        collectObjectsAndLinks(toInsert, toUpdate, toDelete, referenceKey, field, toInsertObjects, toUpdateObjects, toDeleteObjects);
    }

    private static void distributeMapObjectsField(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToInsert,
                                                  final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToDelete,
                                                  final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId,
                                                  final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate,
                                                  final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                                  final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                                  final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                                  final Object referenceKey, final XdStorageObjectField field,
                                                  final Map<?, ?> oldValueOfField, final Map<?, ?> newValueOfField,
                                                  final XdStorageObjectIdField propertyIdField) {
        final Map<Object, Object[]> oldObjects = new HashMap<>();
        for (final Map.Entry<?, ?> entry : oldValueOfField.entrySet()) {
            final Object key = entry.getKey();
            final Object object = entry.getValue();
            final Object id = propertyIdField.get(object);
            oldObjects.put(id != null ? id : object, new Object[]{key, object});
        }

        final Map<Object, Object[]> newObjects = new HashMap<>();
        for (final Map.Entry<?, ?> entry : newValueOfField.entrySet()) {
            final Object key = entry.getKey();
            final Object object = entry.getValue();
            final Object id = propertyIdField.get(object);
            newObjects.put(id != null ? id : object, new Object[]{key, object});
        }

        Collection<XdStoragePair<XdStorageObjectField, Object[]>> toInsertLinks = linksToInsertWithoutId.get(referenceKey);
        if (toInsertLinks == null) {
            linksToInsertWithoutId.put(referenceKey, toInsertLinks = new ArrayList<>());
        }
        Collection<XdStoragePair<XdStorageObjectField, Object[]>> toUpdateLinks = linksToUpdate.get(referenceKey);
        if (toUpdateLinks == null) {
            linksToUpdate.put(referenceKey, toUpdateLinks = new ArrayList<>());
        }

        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToInsertList = fkToInsert.computeIfAbsent(referenceKey, key -> new ArrayList<>());
        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToDeleteList = fkToDelete.computeIfAbsent(referenceKey, key -> new ArrayList<>());

        boolean hasToUpdate = false;
        final Collection<Object> toInsertObjects = new ArrayList<>();
        final Collection<Object> toUpdateObjects = new ArrayList<>();
        final Collection<Object> toDeleteObjects = new ArrayList<>();
        for (final Map.Entry<Object, Object[]> entry : newObjects.entrySet()) {
            if (oldObjects.containsKey(entry.getKey())) {
                toUpdateObjects.add(entry.getValue()[1]);
                oldObjects.remove(entry.getKey());

                toUpdateLinks.add(new XdStoragePair<>(field, entry.getValue()));
                hasToUpdate = true;
            } else {
                final Object object = entry.getValue()[1];
                toInsertObjects.add(object);

                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(object.getClass());
                final XdStorageObjectIdField idField = clInfo.getIdField();

                if(idField.get(object) == null) {
                    toInsertLinks.add(new XdStoragePair<>(field, entry.getValue()));
                } else {
                    fkToInsertList.add(new XdStoragePair<>(field, entry.getValue()[1]));

                    toUpdateLinks.add(new XdStoragePair<>(field, entry.getValue()));
                    hasToUpdate = true;
                }
            }
        }
        oldObjects.values().forEach(arr -> {
            toDeleteObjects.add(arr[1]);
            fkToDeleteList.add(new XdStoragePair<>(field, arr[1]));
        });

        collectObjectsAndLinks(toInsert, toUpdate, toDelete, referenceKey, field, toUpdateLinks, hasToUpdate, toInsertObjects, toUpdateObjects, toDeleteObjects);
    }

    private static void distributeCollectionObjectsField(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToInsert,
                                                         final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToDelete,
                                                         final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId,
                                                         final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate,
                                                         final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                                         final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                                         final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                                         final Object referenceKey, final XdStorageObjectField field,
                                                         final Collection<?> oldValueOfField, final Collection<?> newValueOfField,
                                                         final XdStorageObjectIdField propertyIdField) {
        boolean isDebugEnabled = log.isDebugEnabled();
        if(isDebugEnabled) log.debug("distributeCollectionObjectsField STARTED");

        final Map<Object, Object> oldObjects = new HashMap<>();
        for (final Object object : oldValueOfField) {
            final Object id = propertyIdField.get(object);
            oldObjects.put(id != null ? id : object, object);
        }

        final Map<Object, Object> newObjects = new HashMap<>();
        for (final Object object : newValueOfField) {
            final Object id = propertyIdField.get(object);
            newObjects.put(id != null ? id : object, object);
        }

        Collection<XdStoragePair<XdStorageObjectField, Object[]>> toInsertLinks = linksToInsertWithoutId.get(referenceKey);
        if (toInsertLinks == null) {
            linksToInsertWithoutId.put(referenceKey, toInsertLinks = new ArrayList<>());
        }
        Collection<XdStoragePair<XdStorageObjectField, Object[]>> toUpdateLinks = linksToUpdate.get(referenceKey);
        if (toUpdateLinks == null) {
            linksToUpdate.put(referenceKey, toUpdateLinks = new ArrayList<>());
        }

        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToInsertList = fkToInsert.computeIfAbsent(referenceKey, key -> new ArrayList<>());
        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToDeleteList = fkToDelete.computeIfAbsent(referenceKey, key -> new ArrayList<>());

        boolean hasToUpdate = false;
        final Collection<Object> toInsertObjects = new ArrayList<>();
        final Collection<Object> toUpdateObjects = new ArrayList<>();
        final Collection<Object> toDeleteObjects = new ArrayList<>();
        for (final Map.Entry<Object, Object> entry : newObjects.entrySet()) {
            final Object key = entry.getKey();
            final Object object = entry.getValue();
            if (oldObjects.containsKey(key)) {
                toUpdateObjects.add(object);
                oldObjects.remove(key);

                toUpdateLinks.add(new XdStoragePair<>(field, new Object[]{object}));
                hasToUpdate = true;

                if(isDebugEnabled) {
                    log.debug("distributeCollectionObjectsField toUpdateObjects ADDED OBJECT \r\n " + object);
                    log.debug("distributeCollectionObjectsField toUpdateLinks ADDED OBJECT \r\n " + object);
                }
            } else {
                toInsertObjects.add(object);

                if(isDebugEnabled) log.debug("distributeCollectionObjectsField toInsertObjects ADDED OBJECT \r\n " + object);

                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(object.getClass());
                final XdStorageObjectIdField idField = clInfo.getIdField();

                if(idField.get(object) == null) {
                    toInsertLinks.add(new XdStoragePair<>(field, new Object[]{entry.getValue()}));

                    if(isDebugEnabled) log.debug("distributeCollectionObjectsField toInsertLinks ADDED OBJECT \r\n " + object);
                } else {
                    fkToInsertList.add(new XdStoragePair<>(field, entry.getValue()));

                    toUpdateLinks.add(new XdStoragePair<>(field, new Object[]{entry.getValue()}));
                    hasToUpdate = true;

                    if(isDebugEnabled) log.debug("distributeCollectionObjectsField toUpdateLinks ADDED OBJECT \r\n " + object);
                }
            }
        }
        toDeleteObjects.addAll(oldObjects.values());
        oldObjects.forEach((id, obj) -> fkToDeleteList.add(new XdStoragePair<>(field, obj)));

        if(isDebugEnabled) {
            oldObjects.values().stream().forEach(object -> {
                log.debug("distributeCollectionObjectsField toDeleteObjects ADDED OBJECT \r\n " + object);
            });
        }

        collectObjectsAndLinks(toInsert, toUpdate, toDelete, referenceKey, field, toUpdateLinks, hasToUpdate, toInsertObjects, toUpdateObjects, toDeleteObjects);

        if(isDebugEnabled) log.debug("distributeCollectionObjectsField FINISHED");
    }

    private static void destributeArrayObjectsField(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToInsert,
                                                    final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToDelete,
                                                    final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId,
                                                    final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate,
                                                    final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                                    final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                                    final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                                    final Object referenceKey, final XdStorageObjectField field,
                                                    final Object oldValueOfField, final Object newValueOfField,
                                                    final Class<?> propertyClass, final XdStorageObjectIdField propertyIdField) {
        final int lengthOfOld = Array.getLength(oldValueOfField);
        final Object[] arrayOfOld = (Object[]) oldValueOfField;
        final int lengthOfNew = Array.getLength(newValueOfField);
        final Object[] arrayOfNew = (Object[]) newValueOfField;

        final Map<Object, Object> oldObjects = new HashMap<>();
        for (int i = 0; i < lengthOfOld; ++i) {
            final Object id = propertyIdField.get(arrayOfOld[i]);
            oldObjects.put(id != null ? id : arrayOfOld[i], arrayOfOld[i]);
        }

        final Map<Object, Object> newObjects = new HashMap<>();
        for (int i = 0; i < lengthOfNew; ++i) {
            final Object id = propertyIdField.get(arrayOfNew[i]);
            newObjects.put(id != null ? id : arrayOfNew[i], arrayOfNew[i]);
        }

        Collection<XdStoragePair<XdStorageObjectField, Object[]>> toInsertLinks = linksToInsertWithoutId.get(referenceKey);
        if (toInsertLinks == null) {
            linksToInsertWithoutId.put(referenceKey, toInsertLinks = new ArrayList<>());
        }
        Collection<XdStoragePair<XdStorageObjectField, Object[]>> toUpdateLinks = linksToUpdate.get(referenceKey);
        if (toUpdateLinks == null) {
            linksToUpdate.put(referenceKey, toUpdateLinks = new ArrayList<>());
        }

        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToInsertList = fkToInsert.computeIfAbsent(referenceKey, key -> new ArrayList<>());
        Collection<XdStoragePair<XdStorageObjectField, Object>> fkToDeleteList = fkToDelete.computeIfAbsent(referenceKey, key -> new ArrayList<>());

        boolean hasToUpdate = false;
        final Collection<Object> toInsertObjects = new ArrayList<>();
        final Collection<Object> toUpdateObjects = new ArrayList<>();
        final Collection<Object> toDeleteObjects = new ArrayList<>();
        for (final Map.Entry<Object, Object> entry : newObjects.entrySet()) {
            if (oldObjects.containsKey(entry.getKey())) {
                toUpdateObjects.add(entry.getValue());
                oldObjects.remove(entry.getKey());

                toUpdateLinks.add(new XdStoragePair<>(field, new Object[]{entry.getValue()}));
                hasToUpdate = true;
            } else {
                final Object object = entry.getValue();
                toInsertObjects.add(object);

                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(object.getClass());
                final XdStorageObjectIdField idField = clInfo.getIdField();

                if(idField.get(object) == null) {
                    toInsertLinks.add(new XdStoragePair<>(field, new Object[]{entry.getValue()}));
                } else {
                    fkToInsertList.add(new XdStoragePair<>(field, entry.getValue()));

                    toUpdateLinks.add(new XdStoragePair<>(field, new Object[]{entry.getValue()}));
                    hasToUpdate = true;
                }
            }
        }
        toDeleteObjects.addAll(oldObjects.values());
        oldObjects.forEach((id, obj) -> fkToDeleteList.add(new XdStoragePair<>(field, obj)));

        collectObjectsAndLinks(toInsert, toUpdate, toDelete, referenceKey, field, toUpdateLinks, hasToUpdate, toInsertObjects, toUpdateObjects, toDeleteObjects);
    }

    private static void collectObjectsAndLinks(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                               final Object referenceKey, final XdStorageObjectField field,
                                               final Collection<XdStoragePair<XdStorageObjectField, Object[]>> toUpdateLinks,
                                               final boolean hasToUpdate,
                                               final Collection<Object> toInsertObjects,
                                               final Collection<Object> toUpdateObjects,
                                               final Collection<Object> toDeleteObjects) {
        if (!hasToUpdate) {
            toUpdateLinks.add(new XdStoragePair<>(field, null));
        }

        collectObjectsAndLinks(toInsert, toUpdate, toDelete, referenceKey, field, toInsertObjects, toUpdateObjects, toDeleteObjects);
    }

    private static void collectObjectsAndLinks(final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toInsert,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toUpdate,
                                               final Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> toDelete,
                                               final Object referenceKey, final XdStorageObjectField field,
                                               final Collection<Object> toInsertObjects,
                                               final Collection<Object> toUpdateObjects,
                                               final Collection<Object> toDeleteObjects) {
        Collection<XdStoragePair<XdStorageObjectField, Object>> objects = toInsert.get(referenceKey);
        if (objects == null) {
            toInsert.put(referenceKey, objects = new ArrayList<>());
        }
        objects.add(new XdStoragePair<>(field, toInsertObjects));

        objects = toUpdate.get(referenceKey);
        if (objects == null) {
            toUpdate.put(referenceKey, objects = new ArrayList<>());
        }
        objects.add(new XdStoragePair<>(field, toUpdateObjects));

        objects = toDelete.get(referenceKey);
        if (objects == null) {
            toDelete.put(referenceKey, objects = new ArrayList<>());
        }
        objects.add(new XdStoragePair<>(field, toDeleteObjects));
    }
}
