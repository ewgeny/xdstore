package org.flib.xdstorage.structure;

import org.flib.xdstorage.annotations.XdStorageObjectId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class XdStorageClassIndexStructure {

    @XdStorageObjectId
    private XdStorageClassIndexStructureId id;

    private Map<String, Class<?>> fields;

    private Map<Class<?>, Map<String, Class<?>>> childrenFields;

    public XdStorageClassIndexStructureId getId() {
        return id;
    }

    public void setId(XdStorageClassIndexStructureId id) {
        this.id = id;
    }

    public Map<String, Class<?>> getFields() {
        return fields;
    }

    public void setFields(final Map<String, Class<?>> fields) {
        this.fields = fields;
    }

    public void addField(final String name, final Class<?> cl) {
        if (fields == null) {
            fields = new HashMap<>();
        }
        fields.put(name, cl);
    }

    public Map<Class<?>, Map<String, Class<?>>> getChildrenFields() {
        return childrenFields;
    }

    public void setChildrenFields(final Map<Class<?>, Map<String, Class<?>>> childrenFields) {
        this.childrenFields = childrenFields;
    }

    public void addChildField(final Class<?> cl, final String name, final Class<?> valueCl) {
        if (childrenFields == null) {
            childrenFields = new HashMap<>();
        }

        Map<String, Class<?>> fields = childrenFields.get(cl);
        if (fields == null) {
            childrenFields.put(cl, fields = new HashMap<>());
        }

        fields.put(name, valueCl);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XdStorageClassIndexStructure that = (XdStorageClassIndexStructure) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(fields, that.fields) &&
                Objects.equals(childrenFields, that.childrenFields);
    }
}
