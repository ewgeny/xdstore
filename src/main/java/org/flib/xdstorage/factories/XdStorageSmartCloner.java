package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

/**
 * Финальный декомпозированный оркестратор маршалинга и клонирования СУБД (Поинт В).
 * Избавлен от прямой работы с рефлексивными полями, делегируя задачи Packer и Unpacker процессорам.
 */
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
        if (clInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
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
                    // Делегируем рефлексивное заполнение полей выделенному Packer-компоненту
                    XdStorageClonerFieldPacker.packFields(result, toClone, clInfo, storage, transaction, this);
                    result.setReference__(false);
                }
            } finally {
                result.unlock__();
            }
        }
        return result;
    }

    @Override
    public Object unwrapAndClone(final Object toCloneMaybeWrapped) throws XdStorageException {
        if (toCloneMaybeWrapped == null) {
            return null;
        }
        final Object toClone = XdStorageObjectUtils.getWrappedObjectOrSameObject(toCloneMaybeWrapped);
        try {
            return unwrapAndCloneForOneLevel(toClone);
        } catch (Exception e) {
            throw new XdStorageException("cannot clone and wrap object", e);
        }
    }

    private Object unwrapAndCloneForOneLevel(final Object toClone) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(toClone.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        if (clInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
            return XdStorageObjectUtils.cloneObject(toClone);
        }

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Object id = idField.get(toClone);

        final Object result = cl.newInstance();
        idField.set(result, id);

        // Делегируем рефлексивное извлечение полей выделенному Unpacker-компоненту
        XdStorageClonerFieldUnpacker.unpackFields(result, toClone, clInfo, this);
        return result;
    }

    @Override
    public void fillAndWrap(final Object reference, final Object object, final IXdStorage storage, final XdStorageTransaction transaction) throws XdStorageException {
        if (reference == null) throw new XdStorageException("reference cannot be null");
        if (object == null) throw new XdStorageException("clonable object cannot be null");

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
        if (clInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
            XdStorageObjectUtils.fillObject(reference, object);
        }

        // Повторно используем декомпозированный Packer-процессор
        XdStorageClonerFieldPacker.packFields(reference, object, clInfo, storage, transaction, this);
    }

    // Внутренние package-private мостики для суб-процессоров
    Object internalCloneAsReferenceAndWrapObject(final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
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

    Object internalUnwrapAndCloneAsReference(final Object object) throws Exception {
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
}
