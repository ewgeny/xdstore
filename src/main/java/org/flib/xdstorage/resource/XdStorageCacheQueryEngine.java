package org.flib.xdstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Декомпозированный высокопроизводительный движок Snapshot-изолированного чтения.
 * Отвечает за вычисление транзакционной видимости версий объектов (MVCC)
 * и потокобезопасное выполнение поисковых предикатов.
 */
public class XdStorageCacheQueryEngine {

    private static final Logger log = LogManager.getLogger(XdStorageCacheQueryEngine.class);

    private XdStorageCacheQueryEngine() {
        // Утилитный класс-движок, запрет инстанцирования
    }

    /**
     * Выполняет параллельное чтение кэша СУБД с проверкой предикатов фильтрации
     * и обеспечением изоляции транзакционных снимков (Snapshot Isolation).
     */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> executePredicateRead(
            final Map<Object, XdStorageCacheVersionRecord> cacheRegistry,
            final Map<String, Map<Object, Object>> readObjectsRegistry,
            final Map<String, Map<Object, IXdStorageIdObservableWrapper>> changesUnidentified,
            final XdStorageTransaction transaction,
            final IXdStoragePredicate<T> predicate,
            final XdStorageObjectIdField field,
            final XdStorageServicesLocator services,
            final IXdStorageCloner cloner) throws XdStorageException {

        final String transactionId = transaction.getTransactionId();

        // ИСПРАВЛЕНИЕ: Мапа чтения Обязана быть ConcurrentHashMap для безопасного parallelStream
        final Map<Object, Object> readObjects = readObjectsRegistry.computeIfAbsent(transactionId,
                id -> new ConcurrentHashMap<>());

        final List<T> result = Collections.synchronizedList(new LinkedList<>());
        final AtomicBoolean hasError = new AtomicBoolean(false);
        final XdStorageException[] exceptions = new XdStorageException[]{null};

        // Асинхронный параллельный расчет видимости версий (MVCC Read)
        cacheRegistry.values().parallelStream().forEach(record -> {
            if (hasError.get()) {
                return;
            }

            Object visibleObject = null;

            // Детекция видимой версии объекта на основе матрицы состояний MVCC
            if (record.change == XdStorageResourceCache.Change.update) {
                if (record.state == XdStorageResourceCache.State.committed) {
                    visibleObject = record.object;
                } else if (record.transaction != null && record.transaction == transaction) {
                    visibleObject = record.newObject; // Видим свои изменения
                } else {
                    visibleObject = record.object;    // Чужие незакоммиченные изменения скрыты
                }
            } else if (record.change == XdStorageResourceCache.Change.insert) {
                if (record.state == XdStorageResourceCache.State.committed) {
                    visibleObject = record.object;
                } else if (record.transaction != null && record.transaction == transaction) {
                    visibleObject = record.newObject; // Видим свою вставку
                }
            } else if (record.change == XdStorageResourceCache.Change.delete) {
                // Если объект удален чужой транзакцией — мы его всё еще видим (Snapshot)
                if (record.state != XdStorageResourceCache.State.committed &&
                        (record.transaction == null || record.transaction != transaction)) {
                    visibleObject = record.object;
                }
            } else {
                visibleObject = record.object; // Обычное состояние чтения
            }

            try {
                if (visibleObject != null) {
                    final Object objectId = field.get(visibleObject);
                    Object objectToReturn = null;
                    Object wrappedObject;

                    // Защита от деградации производительности: если объект уже клонирован для этой транзакции,
                    // берем его из readObjects, иначе — оборачиваем оригинальный snapshot в unmodifiable-прокси
                    if (readObjects.containsKey(objectId)) {
                        objectToReturn = readObjects.get(objectId);
                        wrappedObject = XdStorageObjectUtils.wrapAsUnmodifiableObject(objectToReturn, services.getStorage(), transaction);
                    } else {
                        wrappedObject = XdStorageObjectUtils.wrapAsUnmodifiableObject(visibleObject, services.getStorage(), transaction);
                    }

                    // Проверяем объект через переданный пользовательский предикат фильтрации
                    if (predicate.passed((T) wrappedObject)) {
                        if (objectToReturn == null) {
                            // Ленивое клонирование и оборачивание объекта в Unit of Work транзакции при прохождении фильтра
                            Object cloned = cloner.cloneAndWrap(visibleObject, services.getStorage(), transaction);
                            readObjects.putIfAbsent(objectId, cloned);
                            objectToReturn = readObjects.get(objectId);
                        }
                        result.add((T) objectToReturn);
                    }
                }
            } catch (final Throwable e) {
                if (!hasError.getAndSet(true)) {
                    exceptions[0] = new XdStorageException("Критический сбой изоляции транзакционного чтения", e);
                }
            }
        });

        if (exceptions[0] != null) {
            throw exceptions[0];
        }

        // Обработка неидентифицированных объектов (New Inserts без установленного ID на момент вставки)
        final Map<Object, IXdStorageIdObservableWrapper> unidentifiedObjects = changesUnidentified.get(transactionId);
        if (unidentifiedObjects != null) {
            unidentifiedObjects.values().parallelStream().forEach(wrapper -> {
                if (hasError.get()) {
                    return;
                }
                try {
                    if (predicate.passed((T) wrapper)) {
                        result.add((T) wrapper);
                    }
                } catch (final Throwable e) {
                    if (!hasError.getAndSet(true)) {
                        exceptions[0] = new XdStorageException("Ошибка фильтрации неидентифицированных транзакционных объектов", e);
                    }
                }
            });

            if (exceptions[0] != null) {
                throw exceptions[0];
            }
        }

        return result;
    }
}
