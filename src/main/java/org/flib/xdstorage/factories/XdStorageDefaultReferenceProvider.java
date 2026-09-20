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

/**
 * Исправленная и абсолютно потокобезопасная реализация транзакционного провайдера ссылок.
 */
public class XdStorageDefaultReferenceProvider implements IXdStorageReferenceProvider {

    private final IXdStorage storage;

    /**
     * Трехуровневая конкурентная карта: [transactionId -> [class -> [objectId -> objectReference]]]
     */
    private final Map<String, Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>>> references = new ConcurrentHashMap<>();

    public XdStorageDefaultReferenceProvider(final IXdStorage storage) {
        this.storage = storage;
    }

    @Override
    public IXdStorageSimpleWrapper getReference(final Class<?> cl, final Object objectId, final IXdStorageTransaction transaction) {
        final Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>> txMap = references.get(transaction.getTransactionId());
        if (txMap == null) return null;

        final Map<Object, IXdStorageSimpleWrapper> classMap = txMap.get(cl);
        if (classMap == null) return null;

        return classMap.get(objectId);
    }

    @Override
    public IXdStorageSimpleWrapper createAndRegisterReference(final Class<?> cl, final XdStorageObjectIdField idField, final Object objectId, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {

        // ИСПРАВЛЕНИЕ: Атомарное каскадное создание вложенных мап без риска Race Condition между транзакциями
        final Map<Class<?>, Map<Object, IXdStorageSimpleWrapper>> txMap = references.computeIfAbsent(
                transaction.getTransactionId(), id -> new ConcurrentHashMap<>()
        );

        final Map<Object, IXdStorageSimpleWrapper> classMap = txMap.computeIfAbsent(
                cl, clazz -> new ConcurrentHashMap<>()
        );

        // Атомарное создание и регистрация инстанса прокси
        return classMap.computeIfAbsent(objectId, idKey -> {
            try {
                // ИСПРАВЛЕНИЕ: Безопасный вызов конструктора для полной поддержки Java 17+
                java.lang.reflect.Constructor<?> constructor = cl.getDeclaredConstructor();
                constructor.setAccessible(true);

                final IXdStorageSimpleWrapper wrapper = XdStorageObjectUtils.wrapAsSimpleObject(constructor.newInstance(), storage, transaction);
                idField.set(wrapper, objectId);
                return wrapper;
            } catch (final Exception e) {
                throw new RuntimeException("Критическая ошибка кодогенерации прокси-ссылки СУБД", e);
            }
        });
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        references.remove(transaction.getTransactionId());
    }
}
