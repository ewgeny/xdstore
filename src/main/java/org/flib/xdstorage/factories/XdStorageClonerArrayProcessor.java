package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Выделенный stateless-компонент декомпозиции (Поинт В).
 * Инкапсулирует рекурсивную обработку и глубокое копирование массивов Java.
 */
public class XdStorageClonerArrayProcessor {

    public static Object cloneAndWrapArray(final Object src, final IXdStorage storage, final IXdStorageTransaction transaction, final XdStorageSmartCloner cloner) throws Exception {
        final int length = Array.getLength(src);
        final Object clone = Array.newInstance(src.getClass().getComponentType(), length);
        for (int i = 0; i < length; ++i) {
            Object value = Array.get(src, i);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // Ничего не делаем для простых типов
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = cloneAndWrapArray(value, storage, transaction, cloner);
            } else if (value instanceof Collection<?>) {
                value = XdStorageClonerCollectionProcessor.cloneAndWrapCollection((Collection<?>) value, storage, transaction, cloner);
            } else if (value instanceof Map<?, ?>) {
                value = XdStorageClonerCollectionProcessor.cloneAndWrapMap((Map<?, ?>) value, storage, transaction, cloner);
            } else {
                value = cloner.internalCloneAsReferenceAndWrapObject(value, storage, transaction);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }

    public static Object unwrapAndCloneArray(final Object src, final XdStorageSmartCloner cloner) throws Exception {
        final int length = Array.getLength(src);
        final Object clone = Array.newInstance(src.getClass().getComponentType(), length);
        for (int i = 0; i < length; ++i) {
            Object value = Array.get(src, i);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // Ничего не делаем
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = unwrapAndCloneArray(value, cloner);
            } else if (value instanceof Collection<?>) {
                value = XdStorageClonerCollectionProcessor.unwrapAndCloneCollection((Collection<?>) value, cloner);
            } else if (value instanceof Map<?, ?>) {
                value = XdStorageClonerCollectionProcessor.unwrapAndCloneMap((Map<?, ?>) value, cloner);
            } else {
                value = cloner.internalUnwrapAndCloneAsReference(value);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }
}
