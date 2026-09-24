package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Выделенный stateless-компонент декомпозиции (Поинт В).
 * Инкапсулирует глубокое копирование структур данных Collection, List и Map.
 */
public class XdStorageClonerCollectionProcessor {

    @SuppressWarnings("unchecked")
    public static Object cloneAndWrapCollection(final Collection<?> src, final IXdStorage storage, final IXdStorageTransaction transaction, final XdStorageSmartCloner cloner) throws Exception {
        final Collection<Object> clone = (Collection<Object>) src.getClass().newInstance();
        for (final Object object : src) {
            final Class<?> cl = object != null ? XdStorageObjectUtils.getEntityClass(object.getClass()) : null;
            if (object == null || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, object)) {
                clone.add(object);
            } else if (cl == Date.class) {
                clone.add(new Date(((Date) object).getTime()));
            } else if (cl.isArray()) {
                clone.add(XdStorageClonerArrayProcessor.cloneAndWrapArray(object, storage, transaction, cloner));
            } else if (object instanceof Collection<?>) {
                clone.add(cloneAndWrapCollection((Collection<?>) object, storage, transaction, cloner));
            } else if (object instanceof Map<?, ?>) {
                clone.add(cloneAndWrapMap((Map<?, ?>) object, storage, transaction, cloner));
            } else {
                clone.add(cloner.internalCloneAsReferenceAndWrapObject(object, storage, transaction));
            }
        }
        return clone;
    }

    @SuppressWarnings("unchecked")
    public static Object cloneAndWrapMap(final Map<?, ?> src, final IXdStorage storage, final IXdStorageTransaction transaction, final XdStorageSmartCloner cloner) throws Exception {
        final Map<Object, Object> clone = (Map<Object, Object>) src.getClass().newInstance();
        for (final Map.Entry<?, ?> entry : src.entrySet()) {
            final Object keyvalue = entry.getKey(), key;
            Class<?> clValue = keyvalue != null ? XdStorageObjectUtils.getEntityClass(keyvalue.getClass()) : null;
            if (keyvalue == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, keyvalue)) {
                key = keyvalue;
            } else if (clValue == Date.class) {
                key = new Date(((Date) keyvalue).getTime());
            } else {
                key = cloner.internalCloneAsReferenceAndWrapObject(keyvalue, storage, transaction);
            }

            Object value = entry.getValue();
            clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // Ничего не делаем
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = XdStorageClonerArrayProcessor.cloneAndWrapArray(value, storage, transaction, cloner);
            } else if (value instanceof Collection<?>) {
                value = cloneAndWrapCollection((Collection<?>) value, storage, transaction, cloner);
            } else if (value instanceof Map<?, ?>) {
                value = cloneAndWrapMap((Map<?, ?>) value, storage, transaction, cloner);
            } else {
                value = cloner.internalCloneAsReferenceAndWrapObject(value, storage, transaction);
            }
            clone.put(key, value);
        }
        return clone;
    }

    @SuppressWarnings("unchecked")
    public static Object unwrapAndCloneCollection(final Collection<?> src, final XdStorageSmartCloner cloner) throws Exception {
        final Collection<Object> clone = (Collection<Object>) src.getClass().newInstance();
        for (final Object object : src) {
            final Class<?> cl = object != null ? XdStorageObjectUtils.getEntityClass(object.getClass()) : null;
            if (object == null || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, object)) {
                clone.add(object);
            } else if (cl == Date.class) {
                clone.add(new Date(((Date) object).getTime()));
            } else if (cl.isArray()) {
                clone.add(XdStorageClonerArrayProcessor.unwrapAndCloneArray(object, cloner));
            } else if (object instanceof Collection<?>) {
                clone.add(unwrapAndCloneCollection((Collection<?>) object, cloner));
            } else if (object instanceof Map<?, ?>) {
                clone.add(unwrapAndCloneMap((Map<?, ?>) object, cloner));
            } else {
                clone.add(cloner.internalUnwrapAndCloneAsReference(object));
            }
        }
        return clone;
    }

    @SuppressWarnings("unchecked")
    public static Object unwrapAndCloneMap(final Map<?, ?> src, final XdStorageSmartCloner cloner) throws Exception {
        final Map<Object, Object> clone = (Map<Object, Object>) src.getClass().newInstance();
        for (final Map.Entry<?, ?> entry : src.entrySet()) {
            final Object keyvalue = entry.getKey(), key;
            Class<?> clValue = keyvalue != null ? XdStorageObjectUtils.getEntityClass(keyvalue.getClass()) : null;
            if (keyvalue == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, keyvalue)) {
                key = keyvalue;
            } else if (clValue == Date.class) {
                key = new Date(((Date) keyvalue).getTime());
            } else {
                key = cloner.internalUnwrapAndCloneAsReference(keyvalue);
            }

            Object value = entry.getValue();
            clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // Ничего не делаем
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = XdStorageClonerArrayProcessor.unwrapAndCloneArray(value, cloner);
            } else if (value instanceof Collection<?>) {
                value = unwrapAndCloneCollection((Collection<?>) value, cloner);
            } else if (value instanceof Map<?, ?>) {
                value = unwrapAndCloneMap((Map<?, ?>) value, cloner);
            } else {
                value = cloner.internalUnwrapAndCloneAsReference(value);
            }

            if (key != null && value != null) {
                clone.put(key, value);
            }
        }
        return clone;
    }
}
