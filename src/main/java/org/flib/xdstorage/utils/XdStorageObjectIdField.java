package org.flib.xdstorage.utils;

import org.flib.xdstorage.annotations.XdStorageObjectFieldProperties;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class XdStorageObjectIdField extends XdStorageObjectField {

    public XdStorageObjectIdField(final Field field, final Method setter, final Method getter, final XdStorageObjectFieldInfo fieldInfo) {
        super(field, setter, getter, fieldInfo);
    }

    public XdStorageIdGeneratorType getIdGeneretorType() {
        if (field.isAnnotationPresent(XdStorageObjectFieldProperties.class)) {
            return field.getAnnotation(XdStorageObjectFieldProperties.class).idGeneratorType();
        }
        return XdStorageIdGeneratorType.CUSTOM_GENERATOR;
    }

    public Class<? extends IXdStorageIdGenerator> getIdGeneratorClass() {
        if (field.isAnnotationPresent(XdStorageObjectFieldProperties.class)) {
            return field.getAnnotation(XdStorageObjectFieldProperties.class).idGeneratorClass();
        }
        return null;
    }
}
