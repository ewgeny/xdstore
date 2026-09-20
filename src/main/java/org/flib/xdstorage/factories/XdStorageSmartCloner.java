package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class XdStorageSmartCloner implements IXdStorageCloner {

    private final IXdStorageReferenceProvider referencesProvider;

    public XdStorageSmartCloner(final IXdStorageReferenceProvider referencesProvider) {
        this.referencesProvider = referencesProvider;
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        referencesProvider.release(transaction);
    }

    @Override
    public Object cloneAndWrap(final Object toClone, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        if (toClone == null) {
            return null;
        }

        try {
            return cloneAndWrapForOneLevel(toClone, storage, transaction);
        } catch (Exception e) {
            throw new XdStorageException("cannot clone and wrap object", e);
        }
    }

    private Object cloneAndWrapForOneLevel(final Object toClone, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Class<?> cl = toClone.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            return XdStorageObjectUtils.cloneObject(toClone);
        }

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Object id = idField.get(toClone);
        IXdStorageSimpleWrapper result = referencesProvider.getReference(cl, id, transaction);
        if (result == null) {
            result = referencesProvider.createAndRegisterReference(cl, idField, id, storage, transaction);
        }

        if (result.isReference__()) {
            result.lock__();
            try {
                if (result.isReference__()) {
                    final Map<String, XdStorageObjectField> fields = clInfo.getFields();
                    for (final XdStorageObjectField field : fields.values()) {
                        if (field.isIdField()) {
                            continue;
                        }
                        final Object value = field.get(toClone);
                        final Class<?> clValue = value != null ? value.getClass() : null;
                        if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                            field.set(result, value);
                        } else if (clValue == Date.class) {
                            field.set(result, new Date(((Date) value).getTime()));
                        } else if (clValue.isArray()) {
                            field.set(result, cloneAndWrapArray(value, storage, transaction));
                        } else if (value instanceof Collection<?>) {
                            field.set(result, cloneAndWrapCollection((Collection<?>) value, storage, transaction));
                        } else if (value instanceof Map<?, ?>) {
                            field.set(result, cloneAndWrapMap((Map<?, ?>) value, storage, transaction));
                        } else {
                            field.set(result, internalCloneAsReferenceAndWrapObject(value, storage, transaction));
                        }
                    }
                    result.setReference__(false);
                }
            } finally {
                result.unlock__();
            }
        }

        return result;
    }

    private Object cloneAndWrapArray(final Object src, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final int length = Array.getLength(src);
        final Object clone = Array.newInstance(src.getClass().getComponentType(), length);
        for (int i = 0; i < length; ++i) {
            Object value = Array.get(src, i);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // do nothing
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = cloneAndWrapArray(value, storage, transaction);
            } else if (value instanceof Collection<?>) {
                value = cloneAndWrapCollection((Collection<?>) value, storage, transaction);
            } else if (value instanceof Map<?, ?>) {
                value = cloneAndWrapMap((Map<?, ?>) value, storage, transaction);
            } else {
                value = internalCloneAsReferenceAndWrapObject(value, storage, transaction);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }

    private Object cloneAndWrapCollection(final Collection<?> src, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Collection<Object> clone = (Collection<Object>) src.getClass().newInstance();
        for (final Object object : src) {
            final Class<?> cl = object != null ? XdStorageObjectUtils.getEntityClass(object.getClass()) : null;
            if (object == null || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, object)) {
                clone.add(object);
            } else if (cl == Date.class) {
                clone.add(new Date(((Date) object).getTime()));
            } else if (cl.isArray()) {
                clone.add(cloneAndWrapArray(object, storage, transaction));
            } else if (object instanceof Collection<?>) {
                clone.add(cloneAndWrapCollection((Collection<?>) object, storage, transaction));
            } else if (object instanceof Map<?, ?>) {
                clone.add(cloneAndWrapMap((Map<?, ?>) object, storage, transaction));
            } else {
                clone.add(internalCloneAsReferenceAndWrapObject(object, storage, transaction));
            }
        }
        return clone;
    }

    private Object cloneAndWrapMap(final Map<?, ?> src, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Map<Object, Object> clone = (Map<Object, Object>) src.getClass().newInstance();
        for (final Map.Entry<?, ?> entry : ((Map<?, ?>) src).entrySet()) {
            final Object keyvalue = entry.getKey(), key;
            Class<?> clValue = keyvalue != null ? XdStorageObjectUtils.getEntityClass(keyvalue.getClass()) : null;
            if (keyvalue == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, keyvalue)) {
                key = keyvalue;
            } else if (clValue == Date.class) {
                key = new Date(((Date) keyvalue).getTime());
            } else {
                key = internalCloneAsReferenceAndWrapObject(keyvalue, storage, transaction);
            }

            Object value = entry.getValue();
            clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                //do nothing
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = cloneAndWrapArray(value, storage, transaction);
            } else if (value instanceof Collection<?>) {
                value = cloneAndWrapCollection((Collection<?>) value, storage, transaction);
            } else if (value instanceof Map<?, ?>) {
                value = cloneAndWrapMap((Map<?, ?>) value, storage, transaction);
            } else {
                value = internalCloneAsReferenceAndWrapObject(value, storage, transaction);
            }

            clone.put(key, value);
        }
        return clone;
    }

    private Object internalCloneAsReferenceAndWrapObject(final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            if (idField != null) {
                final Object id = idField.get(object);
                if (id == null) {
                    return XdStorageObserverService.getObservableWrapper(object);
                }
            }
            return XdStorageObjectUtils.cloneObject(object);
        }

        final Object id = idField.get(object);
        if (id == null) {
            return XdStorageObserverService.getObservableWrapper(object);
        }

        Object result = referencesProvider.getReference(cl, id, transaction);
        if (result == null) {
            result = referencesProvider.createAndRegisterReference(cl, idField, id, storage, transaction);
        }
        return result;
    }

    @Override
    public Object unwrapAndClone(final Object toCloneMaybeWrapped) throws XdStorageException {
        if (toCloneMaybeWrapped == null) {
            return null;
        }

        final Object toClone = unwrapSimpleObject(toCloneMaybeWrapped);

        try {
            return unwrapAndCloneForOneLevel(toClone);
        } catch (Exception e) {
            throw new XdStorageException("cannot clone and wrap object", e);
        }
    }

    private Object unwrapAndCloneForOneLevel(final Object toClone) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(toClone.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            return XdStorageObjectUtils.cloneObject(toClone);
        }

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Object id = idField.get(toClone);

        final Object result = cl.newInstance();
        idField.set(result, id);

        final Map<String, XdStorageObjectField> fields = clInfo.getFields();
        for (final XdStorageObjectField field : fields.values()) {
            if (field.isIdField()) {
                continue;
            }
            final Object value = field.get(toClone);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                field.set(result, value);
            } else if (clValue == Date.class) {
                field.set(result, new Date(((Date) value).getTime()));
            } else if (clValue.isArray()) {
                field.set(result, unwrapAndCloneArray(value));
            } else if (value instanceof Collection<?>) {
                field.set(result, unwrapAndCloneCollection((Collection<?>) value));
            } else if (value instanceof Map<?, ?>) {
                field.set(result, unwrapAndCloneMap((Map<?, ?>) value));
            } else {
                field.set(result, internalUnwrapAndCloneAsReference(value));
            }
        }
        return result;
    }

    private Object unwrapAndCloneArray(final Object src) throws Exception {
        final int length = Array.getLength(src);
        final Object clone = Array.newInstance(src.getClass().getComponentType(), length);
        for (int i = 0; i < length; ++i) {
            Object value = Array.get(src, i);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                // do nothing
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = unwrapAndCloneArray(value);
            } else if (value instanceof Collection<?>) {
                value = unwrapAndCloneCollection((Collection<?>) value);
            } else if (value instanceof Map<?, ?>) {
                value = unwrapAndCloneMap((Map<?, ?>) value);
            } else {
                value = internalUnwrapAndCloneAsReference(value);
            }
            Array.set(clone, i, value);
        }
        return clone;
    }

    private Object unwrapAndCloneCollection(final Collection<?> src) throws Exception {
        final Collection<Object> clone = (Collection<Object>) src.getClass().newInstance();
        for (final Object object : src) {
            final Class<?> cl = object != null ? XdStorageObjectUtils.getEntityClass(object.getClass()) : null;
            if (object == null || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, object)) {
                clone.add(object);
            } else if (cl == Date.class) {
                clone.add(new Date(((Date) object).getTime()));
            } else if (cl.isArray()) {
                clone.add(unwrapAndCloneArray(object));
            } else if (object instanceof Collection<?>) {
                clone.add(unwrapAndCloneCollection((Collection<?>) object));
            } else if (object instanceof Map<?, ?>) {
                clone.add(unwrapAndCloneMap((Map<?, ?>) object));
            } else {
                clone.add(internalUnwrapAndCloneAsReference(object));
            }
        }
        return clone;
    }

    private Object unwrapAndCloneMap(final Map<?, ?> src) throws Exception {
        final Map<Object, Object> clone = (Map<Object, Object>) src.getClass().newInstance();
        for (final Map.Entry<?, ?> entry : ((Map<?, ?>) src).entrySet()) {
            final Object keyvalue = entry.getKey(), key;
            Class<?> clValue = keyvalue != null ? XdStorageObjectUtils.getEntityClass(keyvalue.getClass()) : null;
            if (keyvalue == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, keyvalue)) {
                key = keyvalue;
            } else if (clValue == Date.class) {
                key = new Date(((Date) keyvalue).getTime());
            } else {
                key = internalUnwrapAndCloneAsReference(keyvalue);
            }

            Object value = entry.getValue();
            clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;
            if (value == null || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                //do nothing
            } else if (clValue == Date.class) {
                value = new Date(((Date) value).getTime());
            } else if (clValue.isArray()) {
                value = unwrapAndCloneArray(value);
            } else if (value instanceof Collection<?>) {
                value = unwrapAndCloneCollection((Collection<?>) value);
            } else if (value instanceof Map<?, ?>) {
                value = unwrapAndCloneMap((Map<?, ?>) value);
            } else {
                value = internalUnwrapAndCloneAsReference(value);
            }

            if (key != null && value != null) {
                clone.put(key, value);
            }
        }
        return clone;
    }

    private Object internalUnwrapAndCloneAsReference(final Object object) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            if (idField != null) {
                final Object id = idField.get(object);
                if (id == null) {
                    return XdStorageObserverService.getObservableWrapper(object);
                }
            }
            return XdStorageObjectUtils.cloneObject(object);
        }

        final Object id = idField.get(object);
        if (id == null) {
            return XdStorageObserverService.getObservableWrapper(object);
        }

        final Object result = cl.newInstance();
        idField.set(result, id);
        return result;
    }

    private Object unwrapSimpleObject(final Object object) {
        return XdStorageObjectUtils.getWrappedObjectOrSameObject(object);
    }

    @Override
    public void fillAndWrap(final Object reference, final Object object, final IXdStorage storage, final XdStorageTransaction transaction) throws XdStorageException {
        if (reference == null) {
            throw new XdStorageException("reference cannot be null");
        }

        if (object == null) {
            throw new XdStorageException("clonable object cannot be null");
        }

        if (reference instanceof IXdStorageSimpleWrapper) {
            final IXdStorageSimpleWrapper lockable = (IXdStorageSimpleWrapper) reference;

            lockable.lock__();
            try {
                fillAndWrapForOneLevel(reference, object, storage, transaction);
            } catch (Exception e) {
                throw new XdStorageException("cannot clone and wrap object", e);
            } finally {
                lockable.unlock__();
            }
        } else {
            try {
                fillAndWrapForOneLevel(reference, object, storage, transaction);
            } catch (Exception e) {
                throw new XdStorageException("cannot clone and wrap object", e);
            }
        }
    }

    private void fillAndWrapForOneLevel(final Object reference, final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(reference.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            XdStorageObjectUtils.fillObject(reference, object);
        }

        final Map<String, XdStorageObjectField> fields = clInfo.getFields();
        for (final XdStorageObjectField field : fields.values()) {
            if (field.isIdField()) {
                continue;
            }
            final Object value = field.get(object);
            final Class<?> clValue = value != null ? value.getClass() : null;
            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                field.set(reference, value);
            } else if (clValue == Date.class) {
                field.set(reference, new Date(((Date) value).getTime()));
            } else if (clValue.isArray()) {
                field.set(reference, cloneAndWrapArray(value, storage, transaction));
            } else if (value instanceof Collection<?>) {
                field.set(reference, cloneAndWrapCollection((Collection<?>) value, storage, transaction));
            } else if (value instanceof Map<?, ?>) {
                field.set(reference, cloneAndWrapMap((Map<?, ?>) value, storage, transaction));
            } else {
                field.set(reference, internalCloneAsReferenceAndWrapObject(value, storage, transaction));
            }
        }
    }
}
