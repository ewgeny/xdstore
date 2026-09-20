package org.flib.xdstorage.search;

import org.flib.xdstorage.utils.*;

import java.util.*;

public class XdStorageSearchIndex {

    private String name;

    private int indexFillingValue;

    private XdStorageObjectIdField idField;

    private XdStorageObjectField primaryField;

    private Map<String, XdStorageObjectField> fieldAccesors;

    /**
     * map[ child_field, child_class_index ]
     */
    private Map<String, String> childrenIndexes;

    /**
     * map[ child_class, list[ pair[field_name, child_accessor ] ] ]
     */
    private Map<Class<?>, List<XdStoragePair<String, XdStorageObjectField>>> childrenAccessors;

    /**
     * map[ child_class, child_id_field_accessor ]
     */
    private Map<Class<?>, XdStorageObjectIdField> childrenIdFields;

    /**
     * map[ child_class, map[ child_field_name, child_field_accessor ]
     */
    private Map<Class<?>, Map<String, XdStorageObjectField>> childrenFieldAccessors;

    public XdStorageSearchIndex(final XdStorageObjectIdField idField, final String name, final int indexFillingValue) {
        this.name = name;
        this.indexFillingValue = indexFillingValue;
        this.idField = idField;
    }

    public String getName() {
        return name;
    }

    public int getIndexFillingValue() {
        return indexFillingValue;
    }

    public XdStorageObjectIdField getIdField() {
        return idField;
    }

    public void setPrimaryField(final XdStorageObjectField primaryField) {
        this.primaryField = primaryField;
    }

    public XdStorageObjectField getPrimaryField() {
        return primaryField;
    }

    public void addFieldAccessor(final String name, final XdStorageObjectField accessor) {
        if (fieldAccesors == null) {
            fieldAccesors = new LinkedHashMap<>();
        }
        fieldAccesors.put(name, accessor);
    }

    public Map<String, XdStorageObjectField> getFieldAccesors() {
        return fieldAccesors == null ? Collections.emptyMap() : fieldAccesors;
    }

    public void addChildIndex(final String fieldName, final String childClassIndexName) {
        if (childrenIndexes == null) {
            childrenIndexes = new HashMap<>();
        }
        childrenIndexes.put(fieldName, childClassIndexName);
    }

    public Map<String, String> getChildrenIndexes() {
        return childrenIndexes == null ? Collections.emptyMap() : childrenIndexes;
    }

    public void addChildAccessor(final String fieldName, final Class<?> cl, final XdStorageObjectField accessor) {
        if (childrenAccessors == null) {
            childrenAccessors = new LinkedHashMap<>();
        }

        List<XdStoragePair<String, XdStorageObjectField>> accessors = childrenAccessors.get(cl);
        if(accessors == null) {
            accessors = new ArrayList<>();
            childrenAccessors.put(cl, accessors);
        }

        accessors.add(new XdStoragePair<>(fieldName, accessor));
    }

    public Collection<List<XdStoragePair<String, XdStorageObjectField>>> getChildAccessors() {
        return childrenAccessors == null ? Collections.emptyList() : childrenAccessors.values();
    }

    public void addChildIdField(final Class<?> cl, final XdStorageObjectIdField idField) {
        if (childrenIdFields == null) {
            childrenIdFields = new LinkedHashMap<>();
        }
        childrenIdFields.put(cl, idField);
    }

    public XdStorageObjectIdField getChildIdField(final Class<?> cl) {
        return childrenIdFields == null ? XdStorageDummyObjectIdField.Instance : childrenIdFields.get(cl);
    }

    public void addChildFieldAccessor(final Class<?> cl, final String name, final XdStorageObjectField accessor) {
        if (childrenFieldAccessors == null) {
            childrenFieldAccessors = new LinkedHashMap<>();
        }

        Map<String, XdStorageObjectField> accessors = childrenFieldAccessors.get(cl);
        if (accessors == null) {
            childrenFieldAccessors.put(cl, accessors = new HashMap<>());
        }
        accessors.put(name, accessor);
    }

    public Map<String, XdStorageObjectField> getChildFieldAccesors(final Class<?> cl) {
        return childrenFieldAccessors == null ? Collections.emptyMap() : childrenFieldAccessors.get(cl);
    }
}
