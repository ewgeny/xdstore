package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.code.XdStorageDummySimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageDefaultReferenceProvider implements IXdStorageReferenceProvider {

    private final IXdStorage storage;
    private final Map<String, Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>>> references = new ConcurrentHashMap<>();

    public XdStorageDefaultReferenceProvider(final IXdStorage storage) {
        this.storage = storage;
    }

    @Override
    public IXdStorageSimpleWrapper getReference(final Class<?> cl, final Object objectId, final IXdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();
        Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>> transactionReferencesMap = references.get(transactionId);
        if (transactionReferencesMap == null) return null;
        Map<Object, IXdStorageSimpleWrapper> transactionClassReferences = transactionReferencesMap.get(cl);
        if (transactionClassReferences == null) return null;
        return transactionClassReferences.get(objectId);
    }

    @Override
    public IXdStorageSimpleWrapper createAndRegisterReference(final Class<?> cl, final XdStorageObjectIdField idField, final Object objectId, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();

        Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>> transactionReferencesMap = references.computeIfAbsent(transactionId, k -> new ConcurrentHashMap<>());
        Map<Object, IXdStorageSimpleWrapper> transactionClassReferences = transactionReferencesMap.computeIfAbsent(cl, k -> new ConcurrentHashMap<>());

        IXdStorageSimpleWrapper result = transactionClassReferences.get(objectId);
        if (result == null) {
            // ФАЗА 1: Упреждающая регистрация фиктивной заглушки Dummy, чтобы разорвать StackOverflow рекурсии полей!
            XdStorageDummySimpleWrapper dummyWrapper = new XdStorageDummySimpleWrapper();
            transactionClassReferences.putIfAbsent(objectId, dummyWrapper);

            try {
                // ФАЗА 2: Спокойно генерируем реальный прокси-класс
                final IXdStorageSimpleWrapper tmp = XdStorageObjectUtils.wrapAsSimpleObject(cl.newInstance(), storage, transaction);
                if (tmp != null) {
                    idField.set(tmp, objectId);
                    // Перезаписываем временную заглушку на полноценный прокси
                    transactionClassReferences.put(objectId, tmp);
                }
                result = transactionClassReferences.get(objectId);
            } catch (final Exception e) {
                transactionClassReferences.remove(objectId); // Чистим при сбое
                throw new XdStorageException(e);
            }
        }
        return result;
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();
        references.remove(transactionId);
    }
}
