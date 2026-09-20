package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageDefaultReferenceProvider implements IXdStorageReferenceProvider {

    private final IXdStorage storage;
    /**
     * [transactionId, [class, [objectId, objectReference]]]
     */
    private final Map<String, Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>>> references = new ConcurrentHashMap<>();

    public XdStorageDefaultReferenceProvider(final IXdStorage storage) {
        this.storage = storage;
    }

    @Override
    public IXdStorageSimpleWrapper getReference(final Class<?> cl, final Object objectId, final IXdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();

        Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>> transactionReferencesMap = references.get(transactionId);
        if (transactionReferencesMap == null) {
            return null;
        }

        Map<Object, IXdStorageSimpleWrapper> transactionClassReferences = transactionReferencesMap.get(cl);
        if (transactionClassReferences == null) {
            return null;
        }

        return transactionClassReferences.get(objectId);
    }

    @Override
    public IXdStorageSimpleWrapper createAndRegisterReference(final Class<?> cl, final XdStorageObjectIdField idField, final Object objectId, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();

        Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>> transactionReferencesMap = references.get(transactionId);
        if (transactionReferencesMap == null) {
            references.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            transactionReferencesMap = references.get(transactionId);
        }

        Map<Object, IXdStorageSimpleWrapper> transactionClassReferences = transactionReferencesMap.get(cl);
        if (transactionClassReferences == null) {
            transactionReferencesMap.putIfAbsent(cl, new ConcurrentHashMap<>());
            transactionClassReferences = transactionReferencesMap.get(cl);
        }

        IXdStorageSimpleWrapper result = transactionClassReferences.get(objectId);
        if (result == null) {
            final IXdStorageSimpleWrapper tmp;
            try {
                tmp = XdStorageObjectUtils.wrapAsSimpleObject(cl.newInstance(), storage, transaction);
            } catch (final Exception e) {
                throw new XdStorageException(e);
            }
            idField.set(tmp, objectId);
            transactionClassReferences.putIfAbsent(objectId, tmp);
            result = transactionClassReferences.get(objectId);
        }

        return result;
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();

        references.remove(transactionId);
    }
}
