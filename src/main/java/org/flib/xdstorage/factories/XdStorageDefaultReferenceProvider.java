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

        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ ЯДРА (Устранение паразитного дублирования MVCC-версий):
        // Сначала выполняем жесткую проверку наличия прокси в реестре сессии. Если ссылка уже
        // существует, мы МГНОВЕННО возвращаем её, категорически предотвращая холостой вызов
        // wrapAsSimpleObject(). Прежняя логика создавала прокси "вслепую", что приводило к регистрации
        // дубликатов-призраков в XdStorageResourceCache и вызывало крах concurrent modification!
        IXdStorageSimpleWrapper result = transactionClassReferences.get(objectId);
        if (result == null) {
            synchronized (transactionClassReferences) { // Блокировка уровня класса для атомарности создания
                result = transactionClassReferences.get(objectId);
                if (result == null) {
                    try {
                        final IXdStorageSimpleWrapper tmp = XdStorageObjectUtils.wrapAsSimpleObject(cl.newInstance(), storage, transaction);
                        if (tmp != null) {
                            idField.set(tmp, objectId);
                        }
                        transactionClassReferences.put(objectId, tmp);
                        result = tmp;
                    } catch (final Exception e) {
                        transactionClassReferences.remove(objectId);
                        throw new XdStorageException(e);
                    }
                }
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
