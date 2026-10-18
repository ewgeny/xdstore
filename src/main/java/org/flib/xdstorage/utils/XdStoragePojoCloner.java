package org.flib.xdstorage.utils;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.code.IXdStorageUnmodifiableWrapper;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageObserverService;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public final class XdStoragePojoCloner {
    private XdStoragePojoCloner() {}

    public static Object clonePojo(final Object parent, final Object src, final Map<Object, Object> visited) throws Exception {
        return clonePojo(parent, src, visited, false);
    }

    public static Object clonePojo(final Object parent, final Object src, final Map<Object, Object> visited, boolean isRootClone) throws Exception {
        if (src == null || src instanceof Class<?>) {
            return src;
        }

        // =========================================================================
        // R-44: РАСПАКОВКА ОБЕРТОК НА ВХОДЕ. Сгенерированные обертки (Observable после
        // save до выдачи DB-id, Simple/Unmodifiable) не имеют no-arg конструктора:
        // cl.newInstance() ниже падал InstantiationException, cloneObject молча возвращал
        // null, и null ЗАПИСЫВАЛСЯ в поле-детей (исчезали вложенные спутники планет
        // под нагрузкой — GraphPhantom flake). Распаковываем до реального POJO.
        // =========================================================================
        if (src instanceof IXdStorageSimpleWrapper
                || src instanceof IXdStorageIdObservableWrapper
                || src instanceof IXdStorageUnmodifiableWrapper) {
            return clonePojo(parent, XdStorageObjectUtils.getWrappedObjectOrSameObject(src), visited, isRootClone);
        }

        final Class<?> cl = src.getClass();

        if (cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, src)) {
            return src;
        }

        if (cl == Date.class) {
            if (visited.containsKey(src)) {
                return visited.get(src);
            }
            Date clonedDate = new Date(((Date) src).getTime());
            visited.put(src, clonedDate);
            return clonedDate;
        }

        // =========================================================================
        // АРХИТЕКТУРНОЕ ИСПРАВЛЕНИЕ (Перехват самостоятельных коллекций на входе):
        // Если сам переданный параметр src является массивом, Collection или Map,
        // мы обязаны МГНОВЕННО перенаправить его в XdStorageCollectionCloner,
        // категорически пресекая рефлексивный разбор JavaBeans-полей самого списка!
        // Это полностью уничтожает IndexOutOfBoundsException в Surefire тесте!
        // =========================================================================
        if (cl.isArray()) {
            return XdStorageCollectionCloner.cloneArray(parent, src, visited);
        }
        if (src instanceof Collection<?>) {
            return XdStorageCollectionCloner.cloneCollection(parent, src, visited);
        }
        if (src instanceof Map<?, ?>) {
            return XdStorageCollectionCloner.cloneMap(parent, src, visited);
        }

        final Object clone;
        if (visited.containsKey(src) && parent != null && parent.getClass() == src.getClass()) {
            clone = parent;
        } else if (visited.containsKey(src)) {
            return visited.get(src);
        } else {
            clone = cl.newInstance();
            visited.put(src, clone);
        }

        final XdStorageClassInfo clInfo = XdStorageClassMetadataRegistry.getClassInfo(cl);
        final XdStorageObjectIdField idField = clInfo.getIdField();

        if (idField != null) {
            final Object id = idField.get(src);
            if (clInfo.getPolicy() != XdStoragePolicy.StoreWithParentObject && id == null) {
                return XdStorageObserverService.getObservableWrapper(src);
            }
            idField.set(clone, id);
        }

        if (clInfo.getPolicy() != XdStoragePolicy.StoreWithParentObject && !isRootClone) {
            return clone;
        }

        for (final XdStorageObjectField field : clInfo.getFields().values()) {
            final Object tmp;
            synchronized (src) { tmp = field.get(src); }

            final Class<?> tmpCl = tmp != null ? tmp.getClass() : null;
            final Object value;
            if (tmp == null || tmpCl.isEnum() || XdStorageObjectUtils.isSimpleType(tmpCl, tmp)) {
                value = tmp;
            } else if (tmpCl == Date.class) {
                if (visited.containsKey(tmp)) {
                    value = visited.get(tmp);
                } else {
                    Date clonedDate = new Date(((Date) tmp).getTime());
                    visited.put(tmp, clonedDate);
                    value = clonedDate;
                }
            } else if (tmpCl.isArray()) {
                value = XdStorageCollectionCloner.cloneArray(clone, tmp, visited);
            } else if (tmp instanceof Collection<?>) {
                value = XdStorageCollectionCloner.cloneCollection(clone, tmp, visited);
            } else if (tmp instanceof Map<?, ?>) {
                value = XdStorageCollectionCloner.cloneMap(clone, tmp, visited);
            } else if (field.isParent()) {
                value = (parent == clone) ? null : parent;
            } else {
                value = clonePojo(clone, tmp, visited);
            }
            field.set(clone, value);
        }
        return clone;
    }
}
