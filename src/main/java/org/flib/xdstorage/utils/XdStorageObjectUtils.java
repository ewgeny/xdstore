package org.flib.xdstorage.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.*;
import org.flib.xdstorage.code.*;
import org.flib.xdstorage.index.XdStorageIndexType;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.object.XdStorageObjectConverter;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class XdStorageObjectUtils {

    private static final Logger log = LogManager.getLogger(XdStorageObjectUtils.class);

    private static final Map<Class<?>, XdStorageClassInfo> classesInfo = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Class<?>> classesUnmodifiableWrappers = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Class<?>> classesSimpleWrappers = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Class<?>> classesObservableWrappers = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Method> checkingWrappableMethod = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Field> simpleWrappersObjectFields = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Field> unmodifiableWrappersObjectFields = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Field> observableWrappersObjectFields = new ConcurrentHashMap<>();

    private XdStorageObjectUtils() {
        // do nothing
    }

    public static <TObject> TObject cloneObject(final TObject object) {
        if (object == null)
            return null;

        try {
            final Class<?> cl = getEntityClass(object.getClass());
            if (cl == Date.class) {
                return (TObject) new Date(((Date) object).getTime());
            }
            if (object instanceof XdStorageIdentifiableObject) {
                return (TObject) XdStorageObjectConverter.convertFrom((XdStorageIdentifiableObject) object);
            }
            if (object instanceof IXdStorageUnmodifiableWrapper) {
                final Field field = getUnmodifiableWrapperObjectField(cl);
                field.setAccessible(true);
                final Object wrappedObject = field.get(object);
                if (wrappedObject instanceof XdStorageIdentifiableObject) {
                    return (TObject) XdStorageObjectConverter.convertFrom((XdStorageIdentifiableObject) wrappedObject);
                }
                return cloneObject((TObject) wrappedObject);
            }

            TObject result;
            fillObject(result = (TObject) cl.newInstance(), object);
            return result;
        } catch (final Exception e) {
            log.warn("filling object error", e);
        }
        return null;
    }

    public static void fillObject(final Object reference, final Object object) {
        if (object instanceof XdStorageIdentifiableObject) {
            XdStorageObjectConverter.fillFrom(reference, (XdStorageIdentifiableObject) object);
        } else {
            fillFromSameTypeObject(reference, object);
        }
    }

    private static void fillFromSameTypeObject(final Object reference, final Object object) {
        final XdStorageClassInfo clInfo = getClassInfo(object.getClass());
        final Collection<XdStorageObjectField> properties = clInfo.getFields().values();
        for (final XdStorageObjectField property : properties) {
            final Object tmp = property.get(object);
            final Class<?> cl = tmp != null ? tmp.getClass() : null;
            if (tmp == null || cl.isPrimitive() || cl.isEnum() || isSimpleType(cl, tmp)) {
                property.set(reference, tmp);
            } else if (cl == Date.class) {
                property.set(reference, new Date(((Date) tmp).getTime()));
            } else if (cl.isArray()) {
                property.set(reference, cloneArray(reference, tmp));
            } else if (tmp instanceof Collection<?>) {
                property.set(reference, cloneCollection(reference, tmp));
            } else if (tmp instanceof Map<?, ?>) {
                property.set(reference, cloneMap(reference, tmp));
            } else {
                property.set(reference, internalCloneObject(reference, tmp));
            }
        }
    }

    public static boolean isWrappableObject(final Object object) {
        try {
            final Class<?> wrapperClass = getClassUnmodifiableWrapper(getEntityClass(object.getClass()));
            return !isDummyWrapperClass(wrapperClass);
        } catch (IOException e) {
            log.warn("checking wrappable error", e);
        }
        return true;
    }

    private static boolean isDummyWrapperClass(final Class<?> wrapperClass) {
        try {
            final Method checkingMethod = getCheckingWrappableMethod(wrapperClass);
            return (Boolean) checkingMethod.invoke(wrapperClass);
        } catch (Exception e) {
            log.warn("checking is dummy wrapper class error", e);
        }
        return true;
    }

    private static boolean isDummySimpleWrapperClass(final Class<?> wrapperClass) {
        try {
            final Method checkingMethod = getCheckingSimpleWrappableMethod(wrapperClass);
            return (Boolean) checkingMethod.invoke(wrapperClass);
        } catch (Exception e) {
            log.warn("checking is dummy simple wrapper class error", e);
        }
        return true;
    }

    private static Method getCheckingWrappableMethod(final Class<?> wrapperClass) {
        if (checkingWrappableMethod.containsKey(wrapperClass))
            return checkingWrappableMethod.get(wrapperClass);

        try {
            final Method method = wrapperClass.getDeclaredMethod("isDummyUnmodifiableWrapper");
            checkingWrappableMethod.putIfAbsent(wrapperClass, method);
        } catch (NoSuchMethodException e) {
            log.warn("no such method error", e);
        }

        return checkingWrappableMethod.get(wrapperClass);
    }

    private static Method getCheckingSimpleWrappableMethod(final Class<?> wrapperClass) {
        if (checkingWrappableMethod.containsKey(wrapperClass))
            return checkingWrappableMethod.get(wrapperClass);

        try {
            final Method method = wrapperClass.getDeclaredMethod("isDummySimpleWrapper");
            checkingWrappableMethod.putIfAbsent(wrapperClass, method);
        } catch (NoSuchMethodException e) {
            log.warn("no such method error", e);
        }

        return checkingWrappableMethod.get(wrapperClass);
    }

    @Deprecated
    public static IXdStorageIdObservableWrapper wrapAsObservableObject(final Object object) {
        if (object == null) {
            return null;
        }

        final Class<?> cl = object.getClass();
        if (IXdStorageIdObservableWrapper.class.isAssignableFrom(cl)) {
            return (IXdStorageIdObservableWrapper) object;
        }

        try {
            final Class<?> wrapperClass = getClassObservableWrapper(cl);
            final Constructor<?> constructor = wrapperClass.getConstructor(cl);
            return (IXdStorageIdObservableWrapper) constructor.newInstance(object);
        } catch (final Exception e) {
            log.warn("wrap object error", e);
        }
        return null;
    }

    private static final Map<Class<?>, Lock> wrapperGenerationLocks = new ConcurrentHashMap<>();

    private static Class<?> getClassObservableWrapper(final Class<?> cl) throws IOException {
        if (classesObservableWrappers.containsKey(cl))
            return classesObservableWrappers.get((cl));

        Lock lock = wrapperGenerationLocks.get(cl);
        if (lock == null) {
            wrapperGenerationLocks.putIfAbsent(cl, new ReentrantLock());
            lock = wrapperGenerationLocks.get(cl);
        }

        lock.lock();
        try {
            final Map<Class<?>, Class<?>> generatedCode = XdStorageClassGenerator.generateObservableWrapper(cl);
            generatedCode.entrySet().forEach(pair -> {
                classesObservableWrappers.putIfAbsent(pair.getKey(), pair.getValue());
            });
        } finally {
            lock.unlock();
        }

        return classesObservableWrappers.get(cl);
    }

    public static boolean isReference(final Object object) {
        return IXdStorageSimpleWrapper.class.isAssignableFrom(object.getClass())
                ? ((IXdStorageSimpleWrapper)object).isReference__() : false;
    }

    public static <TObject> IXdStorageSimpleWrapper wrapAsSimpleObject(final TObject object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        if (object == null) {
            return null;
        }

        final Class<?> cl = object.getClass();
        if (IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) {
            return (IXdStorageSimpleWrapper) object;
        }

        final Class<?> wrapperClass = getClassSimpleWrapper(cl);
        if (isDummySimpleWrapperClass(wrapperClass)) {
            return null;
        }
        final Constructor<?> constructor = wrapperClass.getConstructor(Object.class, cl, IXdStorage.class, IXdStorageTransaction.class);
        return (IXdStorageSimpleWrapper) constructor.newInstance(null, object, storage, transaction);
    }

    public static boolean isSimpleWrappedObject(final Object object) {
        final Class<?> cl = object.getClass();
        return IXdStorageSimpleWrapper.class.isAssignableFrom(cl);
    }

    private static Class<?> getClassSimpleWrapper(final Class<?> cl) throws IOException {
        if (classesSimpleWrappers.containsKey(cl))
            return classesSimpleWrappers.get((cl));

        Lock lock = wrapperGenerationLocks.get(cl);
        if (lock == null) {
            wrapperGenerationLocks.putIfAbsent(cl, new ReentrantLock());
            lock = wrapperGenerationLocks.get(cl);
        }

        lock.lock();
        try {
            final Map<Class<?>, Class<?>> generatedCode = XdStorageClassGenerator.generateSimpleWrapper(cl);
            if (generatedCode.isEmpty()) {
                classesSimpleWrappers.putIfAbsent(cl, XdStorageDummySimpleWrapper.class);
            } else {
                generatedCode.entrySet().forEach(pair -> {
                    classesSimpleWrappers.putIfAbsent(pair.getKey(), pair.getValue());
                });
            }
        } finally {
            lock.unlock();
        }

        return classesSimpleWrappers.get(cl);
    }

    public static <TObject> TObject wrapAsUnmodifiableObject(final TObject object, final IXdStorage storage, final IXdStorageTransaction transaction) {
        return wrapAsUnmodifiableObject(null, object, storage, transaction);
    }

    public static <TObject> TObject wrapAsUnmodifiableObject(final Object parent, TObject object, final IXdStorage storage, final IXdStorageTransaction transaction) {
        if (object == null) {
            return null;
        }
        final Class<?> cl = object.getClass();

        try {
            if (cl == Class.class || cl == Object.class || isSimpleType(cl, null)) {
                return object;
            } else if (cl.isArray()) {
                return wrapArrayAsUnmodifiableObjects(parent, object, storage, transaction);
            } else if (object instanceof Collection<?>) {
                return (TObject) wrapCollectionAsUnmodifiableObjects(parent, (Collection<?>) object, storage, transaction);
            } else if (object instanceof Map<?, ?>) {
                return (TObject) wrapMapAsUnmodifiableObjects(parent, (Map<?, ?>) object, storage, transaction);
            }
            final Class<?> toWrapCl;
            if (cl == XdStorageIdentifiableObject.class) {
                toWrapCl = ((XdStorageIdentifiableObject) object).getType();
            } else if (IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) {
                toWrapCl = getEntityClass(cl);
                object = getWrappedObjectOrSameObject(object);
            } else {
                toWrapCl = cl;
            }
            final Class<?> wrapperClass = getClassUnmodifiableWrapper(toWrapCl);
            if (isDummyWrapperClass(wrapperClass)) {
                return null;
            }
            final Constructor<?> constructor = wrapperClass.getConstructor(Object.class, toWrapCl, IXdStorage.class, IXdStorageTransaction.class);
            return (TObject) constructor.newInstance(parent, object, storage, transaction);
        } catch (final Exception e) {
            log.warn("wrap object error", e);
        }
        return null;
    }

    private static <TObject> TObject wrapArrayAsUnmodifiableObjects(final Object parent, final TObject arr, final IXdStorage storage, final IXdStorageTransaction transaction) {
        final Class<?> arrType = arr.getClass().getComponentType();
        final Object[] src = (Object[]) arr;
        final Object[] dst = (Object[]) Array.newInstance(arrType, src.length);
        for (int i = 0; i < src.length; ++i) {
            dst[i] = wrapAsUnmodifiableObject(parent, src[i], storage, transaction);
        }
        return (TObject) dst;
    }

    private static Collection<?> wrapCollectionAsUnmodifiableObjects(final Object parent, final Collection<?> src, final IXdStorage storage, final IXdStorageTransaction transaction)
            throws IllegalAccessException, InstantiationException {
        if (src.isEmpty()) {
            return Collections.emptyList();
        }

        final Collection<Object> clone = (Collection<Object>) src.getClass().newInstance();
        for (final Object object : src) {
            final Class<?> cl = object != null ? object.getClass() : null;
            if (object == null || cl.isEnum() || isSimpleType(cl, object)) {
                clone.add(object);
            } else if (cl == Date.class) {
                clone.add(new Date(((Date) object).getTime()));
            } else if (cl.isArray()) {
                clone.add(wrapArrayAsUnmodifiableObjects(parent, object, storage, transaction));
            } else if (object instanceof Collection<?>) {
                clone.add(wrapCollectionAsUnmodifiableObjects(parent, (Collection<?>) object, storage, transaction));
            } else if (object instanceof Map<?, ?>) {
                clone.add(wrapMapAsUnmodifiableObjects(parent, (Map<?, ?>) object, storage, transaction));
            } else {
                clone.add(wrapAsUnmodifiableObject(parent, object, storage, transaction));
            }
        }
        return clone;
    }

    private static Map<?, ?> wrapMapAsUnmodifiableObjects(final Object parent, final Map<?, ?> src, final IXdStorage storage, final IXdStorageTransaction transaction)
            throws IllegalAccessException, InstantiationException {
        if (src.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<Object, Object> clone = (Map<Object, Object>) src.getClass().newInstance();
        for (final Map.Entry<?, ?> entry : ((Map<?, ?>) src).entrySet()) {

            final Object keytmp = entry.getKey(), key;
            Class<?> cl = keytmp != null ? keytmp.getClass() : null;
            if (keytmp == null || cl.isEnum() || isSimpleType(cl, keytmp)) {
                key = keytmp;
            } else if (cl == Date.class) {
                key = new Date(((Date) keytmp).getTime());
            } else {
                key = wrapAsUnmodifiableObject(parent, keytmp, storage, transaction);
            }

            final Object valuetmp = entry.getValue(), value;
            cl = valuetmp != null ? valuetmp.getClass() : null;
            if (valuetmp == null || cl.isEnum() || isSimpleType(cl, valuetmp)) {
                value = valuetmp;
            } else if (cl == Date.class) {
                value = new Date(((Date) valuetmp).getTime());
            } else if (cl.isArray()) {
                value = wrapArrayAsUnmodifiableObjects(parent, valuetmp, storage, transaction);
            } else if (valuetmp instanceof Collection<?>) {
                value = wrapCollectionAsUnmodifiableObjects(parent, (Collection<?>) valuetmp, storage, transaction);
            } else if (valuetmp instanceof Map<?, ?>) {
                value = wrapMapAsUnmodifiableObjects(parent, (Map<?, ?>) valuetmp, storage, transaction);
            } else {
                value = wrapAsUnmodifiableObject(parent, valuetmp, storage, transaction);
            }

            clone.put(key, value);
        }
        return clone;
    }

    private static Class<?> getClassUnmodifiableWrapper(final Class<?> cl) throws IOException {
        if (classesUnmodifiableWrappers.containsKey(cl))
            return classesUnmodifiableWrappers.get((cl));

        Lock lock = wrapperGenerationLocks.get(cl);
        if (lock == null) {
            wrapperGenerationLocks.putIfAbsent(cl, new ReentrantLock());
            lock = wrapperGenerationLocks.get(cl);
        }

        lock.lock();
        try {
            final Map<Class<?>, Class<?>> generatedCode = XdStorageClassGenerator.generateUnmodifiableWrapper(cl);
            if (generatedCode.isEmpty()) {
                classesUnmodifiableWrappers.putIfAbsent(cl, XdStorageDummyUnmodifiableWrapper.class);
            } else {
                generatedCode.entrySet().forEach(pair -> {
                    classesUnmodifiableWrappers.putIfAbsent(pair.getKey(), pair.getValue());
                });
            }
        } finally {
            lock.unlock();
        }

        return classesUnmodifiableWrappers.get(cl);
    }

    private static Object cloneArray(final Object parent, final Object array) {
        final int length = Array.getLength(array);
        final Object clone = Array.newInstance(array.getClass().getComponentType(), length);
        for (int i = 0; i < length; ++i) {
            final Object tmp = Array.get(array, i), value;
            final Class<?> cl = tmp != null ? tmp.getClass() : null;
            if (tmp == null || cl.isPrimitive() || cl.isEnum() || isSimpleType(cl, tmp)) {
                value = tmp;
            } else if (cl == Date.class) {
                value = new Date(((Date) tmp).getTime());
            } else if (cl.isArray()) {
                value = cloneArray(parent, tmp);
            } else if (tmp instanceof Collection<?>) {
                value = cloneCollection(parent, tmp);
            } else if (tmp instanceof Map<?, ?>) {
                value = cloneMap(parent, tmp);
            } else {
                value = internalCloneObject(parent, tmp);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }

    @SuppressWarnings("unchecked")
    private static Object cloneCollection(final Object parent, final Object collection) {
        try {
            final Collection<Object> clone = (Collection<Object>) collection.getClass().newInstance();
            for (final Object object : (Collection<?>) collection) {
                final Class<?> cl = object != null ? object.getClass() : null;
                if (object == null || cl.isEnum() || isSimpleType(cl, object)) {
                    clone.add(object);
                } else if (cl == Date.class) {
                    clone.add(new Date(((Date) object).getTime()));
                } else if (cl.isArray()) {
                    clone.add(cloneArray(parent, object));
                } else if (object instanceof Collection<?>) {
                    clone.add(cloneCollection(parent, object));
                } else if (object instanceof Map<?, ?>) {
                    clone.add(cloneMap(parent, object));
                } else {
                    clone.add(internalCloneObject(parent, object));
                }
            }
            return clone;
        } catch (Exception e) {
            log.warn("transform collection error", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object cloneMap(final Object parent, final Object map) {
        try {
            final Map<Object, Object> clone = (Map<Object, Object>) map.getClass().newInstance();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {

                final Object keytmp = entry.getKey(), key;
                Class<?> cl = keytmp != null ? keytmp.getClass() : null;
                if (keytmp == null || cl.isEnum() || isSimpleType(cl, keytmp)) {
                    key = keytmp;
                } else if (cl == Date.class) {
                    key = new Date(((Date) keytmp).getTime());
                } else if (cl.isArray()) {
                    key = cloneArray(parent, keytmp);
                } else if (keytmp instanceof Collection<?>) {
                    key = cloneCollection(parent, keytmp);
                } else if (keytmp instanceof Map<?, ?>) {
                    key = cloneMap(parent, keytmp);
                } else {
                    key = internalCloneObject(parent, keytmp);
                }

                final Object valuetmp = entry.getValue(), value;
                cl = valuetmp != null ? valuetmp.getClass() : null;
                if (valuetmp == null || cl.isEnum() || isSimpleType(cl, valuetmp)) {
                    value = valuetmp;
                } else if (cl == Date.class) {
                    value = new Date(((Date) valuetmp).getTime());
                } else if (cl.isArray()) {
                    value = cloneArray(parent, valuetmp);
                } else if (valuetmp instanceof Collection<?>) {
                    value = cloneCollection(parent, valuetmp);
                } else if (valuetmp instanceof Map<?, ?>) {
                    value = cloneMap(parent, valuetmp);
                } else {
                    value = internalCloneObject(parent, valuetmp);
                }

                clone.put(key, value);
            }
            return clone;
        } catch (Exception e) {
            log.warn("clone object map error", e);
        }
        return null;
    }

    private static Object internalCloneObject(final Object parent, final Object object) {
        try {
            if (object instanceof Class<?>) {
                return object;
            }

            final Class<?> cl = object.getClass();
            if (IXdStorageUnmodifiableWrapper.class.isAssignableFrom(cl)) {
                final Field field = getUnmodifiableWrapperObjectField(cl);
                field.setAccessible(true);
                return internalCloneObject(parent, field.get(object));
            } else if (IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) {
                final Field field = getSimpleWrapperObjectField(cl);
                field.setAccessible(true);
                return internalCloneObject(parent, field.get(object));
            } else if (IXdStorageIdObservableWrapper.class.isAssignableFrom(cl)) {
                final XdStorageClassInfo clInfo = getClassInfo(cl);
                final XdStorageObjectIdField idField = clInfo.getIdField();

                final Object id = idField.get(object);
                if (id == null) {
                    return object;
                }

                final Field field = getObservableWrapperObjectField(cl);
                field.setAccessible(true);
                return internalCloneObject(parent, field.get(object));
            }

            final XdStorageClassInfo clInfo = getClassInfo(cl);
            final XdStorageObjectIdField idField = clInfo.getIdField();

            final Object clone = cl.newInstance();

            if (idField != null) {
                final Object id = idField.get(object);
                if (clInfo.getPolicy() != XdStoragePolicy.StoreWithParentObject) {
                    if (id == null) {
                        return XdStorageObserverService.getObservableWrapper(object);
                    }
                }
                idField.set(clone, id);
            }

            if (clInfo.getPolicy() != XdStoragePolicy.StoreWithParentObject) {
                return clone;
            }

            final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
            for (final XdStorageObjectField field : fields) {
                final Object tmp = field.get(object), value;
                final Class<?> tmpCl = tmp != null ? tmp.getClass() : null;
                if (tmp == null || tmpCl.isEnum() || isSimpleType(tmpCl, tmp)) {
                    value = tmp;
                } else if (tmpCl == Date.class) {
                    value = new Date(((Date) tmp).getTime());
                } else if (tmpCl.isArray()) {
                    value = cloneArray(clone, tmp);
                } else if (tmp instanceof Collection<?>) {
                    value = cloneCollection(clone, tmp);
                } else if (tmp instanceof Map<?, ?>) {
                    value = cloneMap(clone, tmp);
                } else if (field.isParent()) {
                    value = parent;
                } else {
                    value = internalCloneObject(clone, tmp);
                }
                field.set(clone, value);
            }
            return clone;
        } catch (Exception e) {
            log.warn("cloneObject object error", e);
        }
        return null;
    }

    public static boolean isSimpleType(final Class<?> cl, final Object object) {
        return cl.isPrimitive() || Number.class.isAssignableFrom(cl)
                || cl == Boolean.class || cl == Character.class || cl == String.class;
    }

    public static Class<?> getEntityClass(final Class<?> cl) {
        if (IXdStorageIdObservableWrapper.class.isAssignableFrom(cl)
                || IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) {
            return cl.getSuperclass();
        }
        return cl;
    }

    public static XdStorageClassInfo getClassInfo(Class<?> cl) {
        cl = getEntityClass(cl);
        if (classesInfo.containsKey(cl))
            return classesInfo.get(cl);

        final XdStorageClassInfo info = new XdStorageClassInfo();

        info.setClazz(cl);

        info.setPolicy(getClassPolicy(cl));
        setIndexInformation(info, cl);
        info.setIdField(getClassIdField(cl));
        final Map<String, XdStorageObjectField> fields;
        info.setFields(fields = getClassFields(cl));
        info.setIndexes(getClassIndexes(cl, fields));

        classesInfo.putIfAbsent(cl, info);
        return classesInfo.get(cl);
    }

    private static void setIndexInformation(final XdStorageClassInfo info, final Class<?> cl) {
        if (cl.isPrimitive()) {
            info.setIndexType(XdStorageIndexType.Hash);
            info.setIndexFillingValue(500);
            return;
        }

        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            if (clazz.isAnnotationPresent(XdStorageObjectIdIndexType.class)) {
                final XdStorageObjectIdIndexType annotation = clazz.getAnnotation(XdStorageObjectIdIndexType.class);
                info.setIndexType(annotation.indexType());
                info.setIndexFillingValue(annotation.t());
                return;
            }
        }

        info.setIndexType(XdStorageIndexType.Hash);
        info.setIndexFillingValue(500);
    }

    public static XdStorageClassInfo getClassObservableInfo(final Class<?> cl) {
        try {
            final Class<?> observableCl = getClassObservableWrapper(cl);
            return getClassInfo(observableCl);
        } catch (final IOException e) {
            log.error("cannot get observable wrapper for " + cl, e);
        }
        return null;
    }

    private static Map<String, XdStorageObjectField> getClassFields(final Class<?> cl) {
        if (IXdStorageUnmodifiableWrapper.class.isAssignableFrom(cl)) {
            return getClassFields(getWrappedClass(cl));
        }
        final Map<String, XdStorageObjectField> properties = new HashMap<String, XdStorageObjectField>();
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            final Method[] methods = clazz.getDeclaredMethods();
            for (final Method getter : methods) {
                final String name = getter.getName();
                if (getter.getParameterTypes().length == 0 && (name.startsWith("get") || name.startsWith("is"))) {
                    final Class<?> tmp = getter.getReturnType();
                    if (tmp == void.class || tmp == Void.class)
                        continue;
                    Method setter;
                    try {
                        if (name.startsWith("get")) {
                            setter = clazz.getMethod("set" + name.substring(3), tmp);
                            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
                            final Field field = clazz.getDeclaredField(fieldName);
                            properties.put(fieldName, new XdStorageObjectField(field, setter, getter, buildFieldInfo(field, getter)));
                        } else {
                            setter = clazz.getMethod("set" + name.substring(2), tmp);
                            final String fieldName = name.substring(2, 3).toLowerCase() + name.substring(3);
                            final Field field = clazz.getDeclaredField(fieldName);
                            properties.put(fieldName, new XdStorageObjectField(field, setter, getter, buildFieldInfo(field, getter)));
                        }
                    } catch (Exception e) {
                        log.debug(String.format("getting properties map of %s error", clazz), e);
                    }
                }
            }
        }
        return properties;
    }

    private static XdStorageObjectFieldInfo buildFieldInfo(final Field field, final Method getter) {
        Class<?> fieldClass = field.getType(), mapKeyClass = null, valueClass;
        boolean isArray = false, isCollection = false, isMap = false;

        if (fieldClass.isArray()) {
            isArray = true;
            valueClass = fieldClass.getComponentType();
        } else if (Collection.class.isAssignableFrom(fieldClass)) {
            isCollection = true;
            final Type genType = getter.getGenericReturnType();
            valueClass = (Class<?>) ((ParameterizedType) genType).getActualTypeArguments()[0];
        } else if (Map.class.isAssignableFrom(fieldClass)) {
            isMap = true;
            final Type genType = getter.getGenericReturnType();
            Object paramType = ((ParameterizedType) genType).getActualTypeArguments()[0];
            mapKeyClass = paramType instanceof ParameterizedType ? ((Class<?>)((ParameterizedType)paramType).getRawType()) : (Class<?>)paramType;
            paramType = ((ParameterizedType) genType).getActualTypeArguments()[1];
            valueClass = paramType instanceof ParameterizedType ? ((Class<?>)((ParameterizedType)paramType).getRawType()) : (Class<?>)paramType;
        } else {
            valueClass = fieldClass;
        }

        return new XdStorageObjectFieldInfo(fieldClass, isArray, isCollection, isMap, mapKeyClass, valueClass);
    }

    private static XdStorageObjectIdField getClassIdField(final Class<?> cl) {
        if (IXdStorageUnmodifiableWrapper.class.isAssignableFrom(cl)) {
            return getClassIdField(getWrappedClass(cl));
        }

        XdStorageObjectIdField result = null;
        try {
            for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
                final Field[] fields = clazz.getDeclaredFields();
                for (final Field field : fields) {
                    if (field.isAnnotationPresent(XdStorageObjectId.class)) {
                        final String name = field.getName();
                        final String methodSuffix = name.substring(0, 1).toUpperCase() + name.substring(1);
                        final Method getter = cl.getMethod("get" + methodSuffix);
                        final Method setter = cl.getMethod("set" + methodSuffix, getter.getReturnType());
                        result = new XdStorageObjectIdField(field, setter, getter, buildFieldInfo(field, getter));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("getting property idgeneration of object error", e);
        }
        return result;
    }

    private static Map<String, XdStorageSearchIndex> getClassIndexes(final Class<?> cl, final Map<String, XdStorageObjectField> fields) {
        if (IXdStorageUnmodifiableWrapper.class.isAssignableFrom(cl)) {
            return getClassIndexes(getWrappedClass(cl), fields);
        }

        if (!cl.isAnnotationPresent(XdStorageObjectSearchIndex.class)) {
            return Collections.emptyMap();
        }

        final Map<String, XdStorageSearchIndex> indexes = new HashMap<>();

        final XdStorageObjectIdField idField = getClassIdField(cl);
        final XdStorageObjectSearchIndex[] indexAnnotations = cl.getAnnotationsByType(XdStorageObjectSearchIndex.class);
        for(final XdStorageObjectSearchIndex annotation : indexAnnotations) {
            final XdStorageSearchIndex index = new XdStorageSearchIndex(idField, annotation.indexName(), annotation.t());
            indexes.put(index.getName(), index);

            int i = 0;
            for(final String fieldName : annotation.indexFieldNames()) {
                final XdStorageObjectField field = fields.get(fieldName);
                if (i == 0) {
                    index.setPrimaryField(field);
                    ++i;
                }
                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();
                final Class<?> type = fieldInfo.getClazz();

                if (isSimpleType(type, null)) {
                    index.addFieldAccessor(fieldName, field);
                } else {
                    log.debug("Generation index: field '" + fieldName + "' of " + type + " is not a simple type and is ignored");
                }
            }

            for(final XdStorageObjectChildSearchIndex childAnnotation : annotation.childrenIndexes()) {
                final String fname = childAnnotation.childFieldName();
                final XdStorageObjectField field = fields.get(fname);
                final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();
                final String fieldClassIndex = childAnnotation.childClassIndexName();

                if (fieldInfo.isArray() || fieldInfo.isCollection()) {
                    final Class<?> type = fieldInfo.getValueClass();
                    if (getClassPolicy(type) == XdStoragePolicy.StoreWithParentObject) {
                        final XdStorageObjectField accessor = fields.get(fname);
                        attachChildClassIndex(childAnnotation.childFieldName(), cl, type, accessor, index, fieldClassIndex);
                    }
                } else if (fieldInfo.isMap()) {
                    final Type valueType = fieldInfo.getValueClass();
                    if (valueType instanceof Class<?>) {
                        final Class<?> typeValue = (Class<?>) valueType;
                        if (getClassPolicy(typeValue) == XdStoragePolicy.StoreWithParentObject) {
                            final XdStorageObjectField accessor = fields.get(fname);
                            attachChildClassIndex(childAnnotation.childFieldName(), cl, typeValue, accessor, index, fieldClassIndex);
                        }
                    }
                } else if (!isSimpleType(fieldInfo.getClazz(), null)) {
                    final XdStorageObjectField accessor = fields.get(fname);
                    attachChildClassIndex(childAnnotation.childFieldName(), cl, fieldInfo.getClazz(), accessor, index, fieldClassIndex);
                }
            }
        }

        return indexes;
    }

    private static void attachChildClassIndex(final String childFieldName, final Class<?> cl, final Class<?> fieldType,
                                              final XdStorageObjectField accessor, final XdStorageSearchIndex index, final String childIndexName) {
        final XdStorageObjectIdField idField = getClassIdField(fieldType);
        final XdStorageObjectSearchIndex[] indexAnnotations = fieldType.getAnnotationsByType(XdStorageObjectSearchIndex.class);
        for(final XdStorageObjectSearchIndex annotation : indexAnnotations) {
            if(annotation.indexName().equals(childIndexName)) {
                index.addChildIndex(childFieldName, childIndexName);
                index.addChildAccessor(childFieldName, fieldType, accessor);
                index.addChildIdField(fieldType, idField);

                final XdStorageClassInfo clInfo = getClassInfo(fieldType);
                final Map<String, XdStorageObjectField> fields = clInfo.getFields();

                for(final String fieldName : annotation.indexFieldNames()) {
                    final XdStorageObjectField field = fields.get(fieldName);
                    final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();
                    final Class<?> type = fieldInfo.getClazz();

                    if (isSimpleType(type, null)) {
                        index.addChildFieldAccessor(fieldType, fieldName, field);
                    } else {
                        log.debug("Generation index: field '" + fieldName + "' of " + type + " is not a simple type and is ignored");
                    }
                }
                break;
            }
        }
    }

    private static XdStorageObjectField findProperty(final Class<?> cl, final String fieldName) {
        return getClassFields(cl).get(fieldName);
    }

    private static XdStoragePolicy getClassPolicy(final Class<?> cl) {
        if (cl.isPrimitive()) {
            return XdStoragePolicy.StoreWithParentObject;
        }
        XdStoragePolicy policy = null;
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            if (clazz.isAnnotationPresent(XdStorageObjectPolicy.class)) {
                final XdStorageObjectPolicy annotation = clazz.getAnnotation(XdStorageObjectPolicy.class);
                policy = annotation.policy();
                break;
            }
        }

        if (policy == null) {
            policy = XdStoragePolicy.StoreWithParentObject;
        }
        return policy;
    }

    private static Class<?> getWrappedClass(final Class<?> clWrapper) {
        for (final Map.Entry<Class<?>, Class<?>> entry : classesUnmodifiableWrappers.entrySet()) {
            if (entry.getValue() == clWrapper) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Field getSimpleWrapperObjectField(final Class<?> clWrapper) {
        if (simpleWrappersObjectFields.containsKey(clWrapper))
            return simpleWrappersObjectFields.get(clWrapper);

        Field field = null;
        try {
            field = clWrapper.getDeclaredField("object");
        } catch (final NoSuchFieldException e) {
            log.error("cannot findUnidentified field object in " + clWrapper);
        }
        simpleWrappersObjectFields.putIfAbsent(clWrapper, field);
        return simpleWrappersObjectFields.get(clWrapper);
    }

    private static Field getUnmodifiableWrapperObjectField(final Class<?> clWrapper) {
        if (unmodifiableWrappersObjectFields.containsKey(clWrapper))
            return unmodifiableWrappersObjectFields.get(clWrapper);

        Field field = null;
        try {
            field = clWrapper.getDeclaredField("object");
        } catch (final NoSuchFieldException e) {
            log.error("cannot findUnidentified field object in " + clWrapper);
        }
        unmodifiableWrappersObjectFields.putIfAbsent(clWrapper, field);
        return unmodifiableWrappersObjectFields.get(clWrapper);
    }

    private static Field getObservableWrapperObjectField(final Class<?> clWrapper) {
        if (observableWrappersObjectFields.containsKey(clWrapper))
            return observableWrappersObjectFields.get(clWrapper);

        Field field = null;
        try {
            field = clWrapper.getDeclaredField("object");
        } catch (final NoSuchFieldException e) {
            log.error("cannot findUnidentified field object in " + clWrapper);
        }
        observableWrappersObjectFields.putIfAbsent(clWrapper, field);
        return observableWrappersObjectFields.get(clWrapper);
    }

    public static <T> T getWrappedObjectOrSameObject(final Object object) {
        final Class<?> cl = object.getClass();
        try {
            if (IXdStorageSimpleWrapper.class.isAssignableFrom(cl)) {
                final Field field = getSimpleWrapperObjectField(cl);
                field.setAccessible(true);
                return (T) getWrappedObjectOrSameObject(field.get(object));
            } else if (IXdStorageUnmodifiableWrapper.class.isAssignableFrom(cl)) {
                final Field field = getUnmodifiableWrapperObjectField(cl);
                field.setAccessible(true);
                return (T) getWrappedObjectOrSameObject(field.get(object));
            } else if (IXdStorageIdObservableWrapper.class.isAssignableFrom(cl)) {
                final Field field = getObservableWrapperObjectField(cl);
                field.setAccessible(true);
                return (T) getWrappedObjectOrSameObject(field.get(object));
            }
        } catch (final IllegalAccessException e) {
            log.error("cannot get field object of " + cl, e);
        }
        return (T) object;
    }
}
