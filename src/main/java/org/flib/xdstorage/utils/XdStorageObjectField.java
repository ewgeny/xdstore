package org.flib.xdstorage.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.annotations.XdStorageObjectFieldProperties;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageParentObject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

public class XdStorageObjectField {

    private static final Logger log = LogManager.getLogger(XdStorageObjectField.class);

    public final Field field;

    private final Method setter;

    private final Method getter;

    private final XdStorageObjectFieldInfo fieldInfo;

    public XdStorageObjectField(final Field field, final Method setter, final Method getter, final XdStorageObjectFieldInfo fieldInfo) {
        this.field = field;
        this.setter = setter;
        this.getter = getter;
        this.fieldInfo = fieldInfo;
    }

    public boolean isIdField() {
        return field.isAnnotationPresent(XdStorageObjectId.class);
    }

    public String getName() {
        return field.getName();
    }

    public boolean isParent() {
        return field.isAnnotationPresent(XdStorageParentObject.class);
    }

    public boolean isStringField() {
        final Class<?> cl = field.getType();
        return cl == String.class || cl == char[].class;
    }

    public long getStringSize() {
        if (field.isAnnotationPresent(XdStorageObjectFieldProperties.class)) {
            return field.getAnnotation(XdStorageObjectFieldProperties.class).length();
        }
        return 4000;
    }

    public XdStorageObjectFieldInfo getFieldInfo() {
        return fieldInfo;
    }

    public <T> void set(final Object obj, final T value) {
        try {
            setter.invoke(obj, value);
        } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            log.error("cannot set property " + getName() + obj != null ? " of " + obj.getClass() : " for null object", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(final Object obj) {
        try {
            return (T) getter.invoke(obj, new Object[]{});
        } catch (final IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            log.error("cannot get property " + getName() + obj != null ? " of " + obj.getClass() : " from null object", e);
        }
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        final String name = getName();
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        XdStorageObjectField other = (XdStorageObjectField) obj;
        final String name = getName(), otherName = other.getName();
        if (name == null) {
            if (otherName != null)
                return false;
        } else if (!name.equals(otherName))
            return false;
        return true;
    }
}