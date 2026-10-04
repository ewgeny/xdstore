package org.flib.xdstorage.utils;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.*;
import org.flib.xdstorage.index.XdStorageIndexType;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.code.IXdStorageUnmodifiableWrapper;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class XdStorageClassMetadataRegistry {
    private static final Map<Class<?>, XdStorageClassInfo> classesInfo = new ConcurrentHashMap<>();
    private static final ReentrantLock REGISTRY_LOCK = new ReentrantLock();

    private XdStorageClassMetadataRegistry() {}

    public static XdStorageClassInfo getClassInfo(Class<?> cl) {
        cl = XdStorageObjectUtils.getEntityClass(cl);
        XdStorageClassInfo info = classesInfo.get(cl);
        if (info != null) return info;

        REGISTRY_LOCK.lock();
        try {
            info = classesInfo.get(cl);
            if (info != null) return info;

            final XdStorageClassInfo newInfo = new XdStorageClassInfo();
            newInfo.setClazz(cl);
            newInfo.setPolicy(getClassPolicy(cl));
            setIndexInformation(newInfo, cl);
            newInfo.setIdField(getClassIdField(cl));

            final Map<String, XdStorageObjectField> fields = XdStorageReflectionFieldScanner.scanFields(cl);
            newInfo.setFields(fields);
            newInfo.setIndexes(XdStorageClassIndexBuilder.buildIndexes(cl, fields));

            classesInfo.put(cl, newInfo);
            return newInfo;
        } finally {
            REGISTRY_LOCK.unlock();
        }
    }

    public static XdStoragePolicy getClassPolicy(final Class<?> cl) {
        if (cl.isPrimitive()) return XdStoragePolicy.StoreWithParentObject;
        XdStoragePolicy policy = null;
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            if (clazz.isAnnotationPresent(XdStorageObjectPolicy.class)) {
                policy = clazz.getAnnotation(XdStorageObjectPolicy.class).policy();
                break;
            }
        }
        return policy != null ? policy : XdStoragePolicy.StoreWithParentObject;
    }

    public static XdStorageObjectIdField getClassIdField(final Class<?> cl) {
        if (IXdStorageUnmodifiableWrapper.class.isAssignableFrom(cl)) {
            return getClassIdField(XdStorageObjectUtils.getWrappedClass(cl));
        }
        try {
            for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
                for (final Field field : clazz.getDeclaredFields()) {
                    if (field.isAnnotationPresent(XdStorageObjectId.class)) {
                        final String name = field.getName();
                        final String suffix = name.substring(0, 1).toUpperCase() + name.substring(1);
                        final Method getter = cl.getMethod("get" + suffix);
                        final Method setter = cl.getMethod("set" + suffix, getter.getReturnType());
                        return new XdStorageObjectIdField(field, setter, getter, buildFieldInfo(field, getter));
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static Map<String, XdStorageObjectField> getClassFields(final Class<?> cl) {
        return XdStorageReflectionFieldScanner.scanFields(cl);
    }

    public static XdStorageObjectFieldInfo buildFieldInfo(final Field field, final Method getter) {
        Class<?> fieldClass = field.getType(), mapKeyClass = null, valueClass;
        boolean isArray = false, isCollection = false, isMap = false;

        if (fieldClass.isArray()) {
            isArray = true;
            valueClass = fieldClass.getComponentType();
        } else if (Collection.class.isAssignableFrom(fieldClass)) {
            isCollection = true;
            valueClass = (Class<?>) ((ParameterizedType) getter.getGenericReturnType()).getActualTypeArguments()[0];
        } else if (Map.class.isAssignableFrom(fieldClass)) {
            isMap = true;
            Object p = ((ParameterizedType) getter.getGenericReturnType()).getActualTypeArguments()[0];
            mapKeyClass = p instanceof ParameterizedType ? ((Class<?>)((ParameterizedType)p).getRawType()) : (Class<?>)p;
            p = ((ParameterizedType) getter.getGenericReturnType()).getActualTypeArguments()[1];
            valueClass = p instanceof ParameterizedType ? ((Class<?>)((ParameterizedType)p).getRawType()) : (Class<?>)p;
        } else {
            valueClass = fieldClass;
        }
        return new XdStorageObjectFieldInfo(fieldClass, isArray, isCollection, isMap, mapKeyClass, valueClass);
    }

    private static void setIndexInformation(final XdStorageClassInfo info, final Class<?> cl) {
        if (cl.isPrimitive()) { info.setIndexType(XdStorageIndexType.Hash); info.setIndexFillingValue(500); return; }
        for (Class<?> sc = cl; !sc.equals(Object.class); sc = sc.getSuperclass()) {
            if (sc.isAnnotationPresent(XdStorageObjectIdIndexType.class)) {
                final XdStorageObjectIdIndexType a = sc.getAnnotation(XdStorageObjectIdIndexType.class);
                info.setIndexType(a.indexType());
                info.setIndexFillingValue(a.t());
                return;
            }
        }
        info.setIndexType(XdStorageIndexType.Hash); info.setIndexFillingValue(500);
    }
}
