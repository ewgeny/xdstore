package io.github.xdstorage.utils;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.index.XdStorageIndexType;
import io.github.xdstorage.search.XdStorageSearchIndex;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class XdStorageClassInfo {

    private Class<?> clazz;

    private XdStoragePolicy policy;

    private XdStorageIndexType indexType;

    private int indexFillingValue;

    private XdStorageObjectIdField idField;

    private Map<String, XdStorageObjectField> fields = new LinkedHashMap<>();

    private Map<String, XdStorageSearchIndex> indexes = new HashMap<>();

    // AUTOSAVE (docs/tasks/2.1.0/22): the @XdStorageAutoSave annotated fields of this class,
    // built by the metadata registry from the scanned fields (inherited included)
    private final java.util.List<XdStorageAutoSaveFieldInfo> autoSaveFields = new java.util.ArrayList<>();

    public XdStorageClassInfo() {
    }

    public XdStorageClassInfo(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    void setClazz(final Class<?> clazz) {
        this.clazz = clazz;
    }

    public XdStoragePolicy getPolicy() {
        return policy;
    }

    void setPolicy(XdStoragePolicy policy) {
        this.policy = policy;
    }

    public XdStorageIndexType getIndexType() {
        return indexType;
    }

    public void setIndexType(XdStorageIndexType indexType) {
        this.indexType = indexType;
    }

    public int getIndexFillingValue() {
        return indexFillingValue;
    }

    public void setIndexFillingValue(int indexFillingValue) {
        this.indexFillingValue = indexFillingValue;
    }

    public XdStorageObjectIdField getIdField() {
        return idField;
    }

    void setIdField(XdStorageObjectIdField idField) {
        this.idField = idField;
    }

    public java.util.List<XdStorageAutoSaveFieldInfo> getAutoSaveFields() {
        return autoSaveFields;
    }

    void addAutoSaveField(final XdStorageAutoSaveFieldInfo info) {
        autoSaveFields.add(info);
    }

    public Map<String, XdStorageObjectField> getFields() {
        return fields;
    }

    void setFields(Map<String, XdStorageObjectField> fields) {
        this.fields = fields;
    }

    public Map<String, XdStorageSearchIndex> getIndexes() {
        return indexes;
    }

    void setIndexes(Map<String, XdStorageSearchIndex> indexes) {
        this.indexes = indexes;
    }
}
