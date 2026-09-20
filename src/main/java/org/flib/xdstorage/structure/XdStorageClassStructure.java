package org.flib.xdstorage.structure;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class XdStorageClassStructure {

    @XdStorageObjectId
    private Class<?> type;

    private XdStoragePolicy policy;

    private Map<String, Class<?>> fields;

    private Map<String, XdStorageClassIndexStructure> indexes;

    public Class<?> getType() {
        return type;
    }

    public void setType(final Class<?> type) {
        this.type = type;
    }

    public XdStoragePolicy getPolicy() {
        return policy;
    }

    public void setPolicy(final XdStoragePolicy policy) {
        this.policy = policy;
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

    public Map<String, XdStorageClassIndexStructure> getIndexes() {
        return indexes;
    }

    public void setIndexes(final Map<String, XdStorageClassIndexStructure> indexes) {
        this.indexes = indexes;
    }

    public void addIndex(final String name, final XdStorageClassIndexStructure index) {
        if (indexes == null) {
            indexes = new HashMap<>();
        }
        indexes.put(name, index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XdStorageClassStructure that = (XdStorageClassStructure) o;
        return Objects.equals(type, that.type) &&
                policy == that.policy &&
                Objects.equals(fields, that.fields) &&
                Objects.equals(indexes, that.indexes);
    }
}
