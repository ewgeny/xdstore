package org.flib.xdstorage.utils;

import java.lang.reflect.Array;
import java.util.*;

public final class XdStorageCollectionCloner {
    private XdStorageCollectionCloner() {}

    public static Object cloneArray(final Object parent, final Object array, final Map<Object, Object> visited) throws Exception {
        final int length = Array.getLength(array);
        final Object clone = Array.newInstance(array.getClass().getComponentType(), length);
        for (int i = 0; i < length; ++i) {
            final Object tmp = Array.get(array, i), value;
            final Class<?> cl = tmp != null ? tmp.getClass() : null;
            if (tmp == null || cl.isPrimitive() || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, tmp)) {
                value = tmp;
            } else if (cl == Date.class) {
                value = new Date(((Date) tmp).getTime());
            } else if (cl.isArray()) {
                value = cloneArray(parent, tmp, visited);
            } else if (tmp instanceof Collection<?>) {
                value = cloneCollection(parent, tmp, visited);
            } else if (tmp instanceof Map<?, ?>) {
                value = cloneMap(parent, tmp, visited);
            } else {
                value = XdStoragePojoCloner.clonePojo(null, tmp, visited);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }

    @SuppressWarnings("unchecked")
    public static Object cloneCollection(final Object parent, final Object collection, final Map<Object, Object> visited) {
        try {
            if (collection == null) return null;
            final Collection<Object> src = (Collection<Object>) collection;
            if (src.isEmpty()) return src.getClass().newInstance();

            final Collection<Object> clone = (Collection<Object>) collection.getClass().newInstance();
            for (final Object object : src) {
                final Class<?> cl = object != null ? object.getClass() : null;
                if (object == null || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, object)) {
                    clone.add(object);
                } else if (cl == Date.class) {
                    clone.add(new Date(((Date) object).getTime()));
                } else if (cl.isArray()) {
                    clone.add(cloneArray(parent, object, visited));
                } else if (object instanceof Collection<?>) {
                    clone.add(cloneCollection(parent, object, visited));
                } else if (object instanceof Map<?, ?>) {
                    clone.add(cloneMap(parent, object, visited));
                } else {
                    clone.add(XdStoragePojoCloner.clonePojo(null, object, visited));
                }
            }
            return clone;
        } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    public static Object cloneMap(final Object parent, final Object map, final Map<Object, Object> visited) {
        try {
            if (map == null) return null;
            final Map<Object, Object> src = (Map<Object, Object>) map;
            if (src.isEmpty()) return src.getClass().newInstance();

            final Map<Object, Object> clone = (Map<Object, Object>) map.getClass().newInstance();
            for (final Map.Entry<?, ?> entry : src.entrySet()) {
                final Object keytmp = entry.getKey(), key;
                Class<?> cl = keytmp != null ? keytmp.getClass() : null;
                if (keytmp == null || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, keytmp)) key = keytmp;
                else if (cl == Date.class) key = new Date(((Date) keytmp).getTime());
                else key = XdStoragePojoCloner.clonePojo(null, keytmp, visited);

                final Object valuetmp = entry.getValue(), value;
                cl = valuetmp != null ? valuetmp.getClass() : null;
                if (valuetmp == null || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, valuetmp)) value = valuetmp;
                else if (cl == Date.class) value = new Date(((Date) valuetmp).getTime());
                else value = XdStoragePojoCloner.clonePojo(null, valuetmp, visited);

                if (key != null) clone.put(key, value);
            }
            return clone;
        } catch (Exception e) { return null; }
    }
}
