package org.flib.xdstorage.utils;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.index.XdStorageIndexType;
import org.flib.xdstorage.search.XdStorageSearchIndex;

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
