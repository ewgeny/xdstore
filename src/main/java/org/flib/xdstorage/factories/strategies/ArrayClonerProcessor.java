package org.flib.xdstorage.factories.strategies;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.lang.reflect.Array;
import java.util.Date;

public class ArrayClonerProcessor implements XdStorageClonerTypeProcessor {
    @Override public boolean supports(Class<?> cl, Object val) { return cl.isArray(); }

    @Override
    public Object cloneAndWrap(Object src, IXdStorage storage, IXdStorageTransaction tx, XdStorageClonerContext context) throws Exception {
        final int length = Array.getLength(src);
        final Object clone = Array.newInstance(src.getClass().getComponentType(), length);

        for (int i = 0; i < length; ++i) {
            Object value = Array.get(src, i);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;

            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // Оставляем как есть
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else {
                value = context.processCloneAndWrap(value, storage, tx);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }

    @Override
    public Object unwrapAndClone(Object src, XdStorageClonerContext context) throws Exception {
        final int length = Array.getLength(src);
        final Object clone = Array.newInstance(src.getClass().getComponentType(), length);

        for (int i = 0; i < length; ++i) {
            Object value = Array.get(src, i);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;

            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // Оставляем как есть
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else {
                value = context.processUnwrapAndClone(value);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }
}
