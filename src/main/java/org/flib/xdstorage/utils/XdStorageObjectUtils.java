package org.flib.xdstorage.utils;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.*;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.object.XdStorageObjectConverter;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

public final class XdStorageObjectUtils {

    private XdStorageObjectUtils() {}

    public static <TObject> TObject cloneObject(final TObject object) {
        return XdStorageMvccGraphCloner.cloneObject(object);
    }

    public static void fillObject(final Object reference, final Object object) {
        if (object == null) throw new NullPointerException("Object cannot be null");
        if (reference == null) throw new NullPointerException("Object reference cannot be null");

        if (object instanceof XdStorageIdentifiableObject) {
            XdStorageObjectConverter.fillFrom(reference, (XdStorageIdentifiableObject) object);
        } else {
            XdStorageMvccGraphCloner.fillFromSameTypeObject(reference, object);
        }
    }

    public static boolean isWrappableObject(final Object object) {
        try {
            final Class<?> w = XdStorageProxyCompilationEngine.getClassUnmodifiableWrapper(getEntityClass(object.getClass()));
            return !((Boolean) XdStorageProxyCompilationEngine.getCheckingWrappableMethod(w).invoke(w));
        } catch (Exception e) { return true; }
    }

    @Deprecated
    public static IXdStorageIdObservableWrapper wrapAsObservableObject(final Object object) {
        if (object == null) return null;
        if (object instanceof IXdStorageIdObservableWrapper) return (IXdStorageIdObservableWrapper) object;
        try {
            return (IXdStorageIdObservableWrapper) XdStorageProxyCompilationEngine.getClassObservableWrapper(object.getClass())
                    .getConstructor(object.getClass()).newInstance(object);
        } catch (Exception e) { return null; }
    }

    public static boolean isReference(final Object object) {
        return object instanceof IXdStorageSimpleWrapper ? ((IXdStorageSimpleWrapper) object).isReference__() : false;
    }

    public static <TObject> IXdStorageSimpleWrapper wrapAsSimpleObject(final TObject object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        if (object == null) return null;
        if (object instanceof IXdStorageSimpleWrapper) return (IXdStorageSimpleWrapper) object;

        final Class<?> w = XdStorageProxyCompilationEngine.getClassSimpleWrapper(object.getClass());
        if ((Boolean) XdStorageProxyCompilationEngine.getCheckingSimpleWrappableMethod(w).invoke(w)) return null;
        return (IXdStorageSimpleWrapper) w.getConstructor(Object.class, object.getClass(), IXdStorage.class).newInstance(null, object, storage);
    }

    public static boolean isSimpleWrappedObject(final Object object) {
        return object instanceof IXdStorageSimpleWrapper;
    }

    public static boolean isSimpleReference(final Object obj) {
        return obj instanceof org.flib.xdstorage.object.XdStorageIdentifiableObject || obj instanceof org.flib.xdstorage.code.IXdStorageSimpleWrapper;
    }

    public static <TObject> TObject wrapAsUnmodifiableObject(final TObject object, final IXdStorage storage, final IXdStorageTransaction transaction) {
        return wrapAsUnmodifiableObject(null, object, storage, transaction);
    }

    @SuppressWarnings("unchecked")
    public static <TObject> TObject wrapAsUnmodifiableObject(final Object parent, TObject object, final IXdStorage storage, final IXdStorageTransaction transaction) {
        if (object == null) return null;
        final Class<?> cl = object.getClass();

        try {
            if (cl == Class.class || cl == Object.class || isSimpleType(cl, null)) return object;
            if (cl.isArray()) return wrapArrayAsUnmodifiableObjects(parent, object, storage, transaction);
            if (object instanceof Collection<?>) return (TObject) wrapCollectionAsUnmodifiableObjects(parent, (Collection<?>) object, storage, transaction);
            if (object instanceof Map<?, ?>) return (TObject) wrapMapAsUnmodifiableObjects(parent, (Map<?, ?>) object, storage, transaction);

            final Class<?> toWrapCl = (cl == XdStorageIdentifiableObject.class) ? ((XdStorageIdentifiableObject) object).getType() : (IXdStorageSimpleWrapper.class.isAssignableFrom(cl) ? getEntityClass(cl) : cl);
            if (IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) object = getWrappedObjectOrSameObject(object);

            final Class<?> w = XdStorageProxyCompilationEngine.getClassUnmodifiableWrapper(toWrapCl);
            if ((Boolean) XdStorageProxyCompilationEngine.getCheckingWrappableMethod(w).invoke(w)) return null;
            return (TObject) w.getConstructor(Object.class, toWrapCl, IXdStorage.class, IXdStorageTransaction.class).newInstance(parent, object, storage, transaction);
        } catch (Exception e) { return null; }
    }

    private static <TObject> TObject wrapArrayAsUnmodifiableObjects(final Object parent, final TObject arr, final IXdStorage storage, final IXdStorageTransaction transaction) {
        final Object[] src = (Object[]) arr;
        final Object[] dst = (Object[]) Array.newInstance(arr.getClass().getComponentType(), src.length);
        for (int i = 0; i < src.length; ++i) dst[i] = wrapAsUnmodifiableObject(parent, src[i], storage, transaction);
        return (TObject) dst;
    }

    private static Collection<?> wrapCollectionAsUnmodifiableObjects(final Object parent, final Collection<?> src, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        if (src.isEmpty()) return Collections.emptyList();
        final Collection<Object> clone = (Collection<Object>) src.getClass().newInstance();
        for (final Object object : src) {
            final Class<?> cl = object != null ? object.getClass() : null;
            if (object == null || cl.isEnum() || isSimpleType(cl, object)) clone.add(object);
            else if (cl == Date.class) clone.add(new Date(((Date) object).getTime()));
            else if (cl.isArray()) clone.add(wrapArrayAsUnmodifiableObjects(parent, object, storage, transaction));
            else if (object instanceof Collection<?>) clone.add(wrapCollectionAsUnmodifiableObjects(parent, (Collection<?>) object, storage, transaction));
            else if (object instanceof Map<?, ?>) clone.add(wrapMapAsUnmodifiableObjects(parent, (Map<?, ?>) object, storage, transaction));
            else clone.add(wrapAsUnmodifiableObject(parent, object, storage, transaction));
        }
        return clone;
    }

    private static Map<?, ?> wrapMapAsUnmodifiableObjects(final Object parent, final Map<?, ?> src, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        if (src.isEmpty()) return Collections.emptyMap();
        final Map<Object, Object> clone = (Map<Object, Object>) src.getClass().newInstance();
        for (final Map.Entry<?, ?> entry : src.entrySet()) {
            final Object keytmp = entry.getKey(), key;
            Class<?> cl = keytmp != null ? keytmp.getClass() : null;
            if (keytmp == null || cl.isEnum() || isSimpleType(cl, keytmp)) key = keytmp;
            else if (cl == Date.class) key = new Date(((Date) keytmp).getTime());
            else key = wrapAsUnmodifiableObject(parent, keytmp, storage, transaction);

            final Object valuetmp = entry.getValue(), value;
            cl = valuetmp != null ? valuetmp.getClass() : null;
            if (valuetmp == null || cl.isEnum() || isSimpleType(cl, valuetmp)) value = valuetmp;
            else if (cl == Date.class) value = new Date(((Date) valuetmp).getTime());
            else if (cl.isArray()) value = wrapArrayAsUnmodifiableObjects(parent, valuetmp, storage, transaction);
            else if (valuetmp instanceof Collection<?>) value = wrapCollectionAsUnmodifiableObjects(parent, (Collection<?>) valuetmp, storage, transaction);
            else if (valuetmp instanceof Map<?, ?>) value = wrapMapAsUnmodifiableObjects(parent, (Map<?, ?>) valuetmp, storage, transaction);
            else value = wrapAsUnmodifiableObject(parent, valuetmp, storage, transaction);

            clone.put(key, value);
        }
        return clone;
    }

    public static boolean isSimpleType(final Class<?> cl, final Object object) {
        return cl.isPrimitive() || Number.class.isAssignableFrom(cl) || cl == Boolean.class || cl == Character.class || cl == String.class;
    }

    public static Class<?> getEntityClass(final Class<?> cl) {
        return (IXdStorageIdObservableWrapper.class.isAssignableFrom(cl) || IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) ? cl.getSuperclass() : cl;
    }

    public static XdStorageClassInfo getClassInfo(Class<?> cl) {
        return XdStorageClassMetadataRegistry.getClassInfo(cl);
    }

    public static Class<?> getMergedClass(final Class<?> clWrapper) {
        return XdStorageProxyCompilationEngine.getWrappedClass(clWrapper);
    }

    public static Class<?> getWrappedClass(final Class<?> clWrapper) {
        return XdStorageProxyCompilationEngine.getWrappedClass(clWrapper);
    }

    public static Field getSimpleWrapperObjectField(final Class<?> clWrapper) {
        return XdStorageWrapperFieldLocator.getSimpleWrapperObjectField(clWrapper);
    }

    public static Field getUnmodifiableWrapperObjectField(final Class<?> clWrapper) {
        return XdStorageWrapperFieldLocator.getUnmodifiableWrapperObjectField(clWrapper);
    }

    public static Field getObservableWrapperObjectField(final Class<?> clWrapper) {
        return XdStorageWrapperFieldLocator.getObservableWrapperObjectField(clWrapper);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getWrappedObjectOrSameObject(final Object object) {
        final Class<?> cl = object.getClass();
        try {
            if (IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) return (T) getWrappedObjectOrSameObject(getSimpleWrapperObjectField(cl).get(object));
            if (IXdStorageUnmodifiableWrapper.class.isAssignableFrom(cl)) return (T) getWrappedObjectOrSameObject(getUnmodifiableWrapperObjectField(cl).get(object));
            if (IXdStorageIdObservableWrapper.class.isAssignableFrom(cl)) return (T) getWrappedObjectOrSameObject(getObservableWrapperObjectField(cl).get(object));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return (T) object;
    }
}