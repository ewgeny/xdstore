package org.flib.xdstorage.utils;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.*;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class XdStorageClassIndexBuilder {
    private XdStorageClassIndexBuilder() {}

    public static Map<String, XdStorageSearchIndex> buildIndexes(final Class<?> cl, final Map<String, XdStorageObjectField> fields) {
        if (!cl.isAnnotationPresent(XdStorageObjectSearchIndex.class)) return Collections.emptyMap();

        final Map<String, XdStorageSearchIndex> indexes = new HashMap<>();
        final XdStorageObjectIdField idField = XdStorageClassMetadataRegistry.getClassIdField(cl);

        for (final XdStorageObjectSearchIndex annotation : cl.getAnnotationsByType(XdStorageObjectSearchIndex.class)) {
            final XdStorageSearchIndex index = new XdStorageSearchIndex(idField, annotation.indexName(), annotation.t());
            indexes.put(index.getName(), index);

            int i = 0;
            for (final String fieldName : annotation.indexFieldNames()) {
                final XdStorageObjectField field = fields.get(fieldName);
                if (i == 0) { index.setPrimaryField(field); ++i; }
                if (XdStorageObjectUtils.isSimpleType(field.getFieldInfo().getClazz(), null)) {
                    index.addFieldAccessor(fieldName, field);
                }
            }

            for (final XdStorageObjectChildSearchIndex childAnnotation : annotation.childrenIndexes()) {
                final String fname = childAnnotation.childFieldName();
                final XdStorageObjectField field = fields.get(fname);
                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();
                final String fieldClassIndex = childAnnotation.childClassIndexName();

                if (fieldInfo.isArray() || fieldInfo.isCollection()) {
                    final Class<?> type = fieldInfo.getValueClass();
                    if (XdStorageClassMetadataRegistry.getClassPolicy(type) == XdStoragePolicy.StoreWithParentObject) {
                        attachChildClassIndex(fname, cl, type, field, index, fieldClassIndex);
                    }
                } else if (fieldInfo.isMap()) {
                    final Type valueType = fieldInfo.getValueClass();
                    if (valueType instanceof Class<?>) {
                        attachChildClassIndex(fname, cl, (Class<?>) valueType, field, index, fieldClassIndex);
                    }
                } else if (!XdStorageObjectUtils.isSimpleType(fieldInfo.getClazz(), null)) {
                    attachChildClassIndex(fname, cl, fieldInfo.getClazz(), field, index, fieldClassIndex);
                }
            }
        }
        return indexes;
    }

    private static void attachChildClassIndex(final String childFieldName, final Class<?> cl, final Class<?> fieldType,
                                              final XdStorageObjectField accessor, final XdStorageSearchIndex index, final String childIndexName) {
        final XdStorageObjectIdField idField = XdStorageClassMetadataRegistry.getClassIdField(fieldType);
        for (final XdStorageObjectSearchIndex annotation : fieldType.getAnnotationsByType(XdStorageObjectSearchIndex.class)) {
            if (annotation.indexName().equals(childIndexName)) {
                index.addChildIndex(childFieldName, childIndexName);
                index.addChildAccessor(childFieldName, fieldType, accessor);
                index.addChildIdField(fieldType, idField);

                final Map<String, XdStorageObjectField> fields = XdStorageClassMetadataRegistry.getClassFields(fieldType);
                for (final String fieldName : annotation.indexFieldNames()) {
                    final XdStorageObjectField field = fields.get(fieldName);
                    if (XdStorageObjectUtils.isSimpleType(field.getFieldInfo().getClazz(), null)) {
                        index.addChildFieldAccessor(fieldType, fieldName, field);
                    }
                }
                break;
            }
        }
    }
}
