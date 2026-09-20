package org.flib.xdstorage.utils;

public class XdStorageObjectFieldInfo {

    private Class<?> clazz;

    private boolean isArray;

    private boolean isCollection;

    private boolean isMap;

    private Class<?> mapKeyClass;

    private Class<?> valueClass;

    public XdStorageObjectFieldInfo(final Class<?> clazz, final boolean isArray, final boolean isCollection, final boolean isMap, final Class<?> mapKeyClass, final Class<?> valueClass) {
        this.clazz = clazz;
        this.isArray = isArray;
        this.isCollection = isCollection;
        this.isMap = isMap;
        this.mapKeyClass = mapKeyClass;
        this.valueClass = valueClass;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public boolean isArray() {
        return isArray;
    }

    public boolean isCollection() {
        return isCollection;
    }

    public boolean isMap() {
        return isMap;
    }

    public Class<?> getMapKeyClass() {
        return mapKeyClass;
    }

    public Class<?> getValueClass() {
        return valueClass;
    }
}
