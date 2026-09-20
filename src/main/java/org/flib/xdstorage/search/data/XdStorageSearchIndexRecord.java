package org.flib.xdstorage.search.data;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.*;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
public class XdStorageSearchIndexRecord {

    @XdStorageObjectId
    private Object id;

    private Object resourceId;

    private Object primaryIndexValue;

    private Map<String, Object> fieldValues;

    /**
     * map[ child_class, map[ child_field_name, list[ object_id ] ] ]
     */
    private Map<Class<?>, Map<String, List<Object>>> childrenIds;

    /**
     * Map[ child_class, map[ child_object_id, Map[ child_field_name, Map[child_object_class_field_name, child_object_field_value]]]]
     */
    private Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> childrenFieldValues;

    public XdStorageSearchIndexRecord() {
    }

    public void setId(final Object id) {
        this.id = id;
    }

    public Object getId() {
        return id;
    }

    public Object getResourceId() {
        return resourceId;
    }

    public void setResourceId(final Object resourceId) {
        this.resourceId = resourceId;
    }

    public void setPrimaryIndexValue(final Object primaryIndexValue) {
        this.primaryIndexValue = primaryIndexValue;
    }

    public Object getPrimaryIndexValue() {
        return primaryIndexValue;
    }

    public void setFieldValue(final String name, final Object value) {
        if (fieldValues == null) {
            fieldValues = new HashMap<>();
        }
        fieldValues.put(name, value);
    }

    public Object getFieldValue(final String name) {
        return fieldValues == null ? null : fieldValues.get(name);
    }

    public void setFieldValues(final Map<String, Object> fieldValues) {
        this.fieldValues = fieldValues;
    }

    public Map<String, Object> getFieldValues() {
        return fieldValues;
    }

    public void addChildIdIfNotExists(final String childFieldName, final Class<?> cl, final Object childId) {
        if (childrenIds == null) {
            childrenIds = new HashMap<>();
        }

        Map<String, List<Object>> idsOfClass = childrenIds.get(cl);
        if (idsOfClass == null) {
            childrenIds.put(cl, idsOfClass = new HashMap<>());
        }

        List<Object> ids = idsOfClass.get(childFieldName);
        if(ids == null) {
            idsOfClass.put(childFieldName, ids = new ArrayList<>());
        }

        if(!ids.contains(childId)) {
            ids.add(childId);
        }
    }

    public void addChildFieldValue(final String childFieldName, final Class<?> cl, final Object objectId, final String fieldName, final Object value) {
        if (childrenFieldValues == null) {
            childrenFieldValues = new HashMap<>();
        }

        Map<Object, Map<String, Map<String, Object>>> objects = childrenFieldValues.get(cl);
        if (objects == null) {
            childrenFieldValues.putIfAbsent(cl, objects = new HashMap<>());
        }

        Map<String, Map<String, Object>> childs = objects.get(objectId);
        if (childs == null) {
            objects.put(objectId, childs = new HashMap<>());
        }

        Map<String, Object> valuesByObjectId = childs.get(childFieldName);
        if(valuesByObjectId == null) {
            childs.put(childFieldName, valuesByObjectId = new HashMap<>());
        }

        valuesByObjectId.put(fieldName, value);

        addChildIdIfNotExists(childFieldName, cl, objectId);
    }

    public Collection<Object> getChildFieldValue(final String childFieldName, final Class<?> cl, final String fieldName) {
        if (childrenFieldValues == null) {
            return Collections.emptyList();
        }

        final Map<Object, Map<String, Map<String, Object>>> objects = childrenFieldValues.get(cl);
        if (objects == null) {
            return Collections.emptyList();
        }

        final Collection<Object> result = new LinkedList<>();
        objects.values().stream().forEach(values -> {
            final Map<String, Object> tmp = values.get(childFieldName);
            if (tmp != null) {
                tmp.forEach((key, value) -> {
                    if (key.equalsIgnoreCase(fieldName)) {
                        result.add(value);
                    }
                });
            }
        });

        return result;
    }

    public void setChildrenFieldValues(final Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> childrenFieldValues) {
        this.childrenFieldValues = childrenFieldValues;
    }

    public Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> getChildrenFieldValues() {
        return childrenFieldValues;
    }
}
