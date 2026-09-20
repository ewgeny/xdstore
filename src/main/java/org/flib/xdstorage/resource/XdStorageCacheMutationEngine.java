package org.flib.xdstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Изолированный декомпозированный компонент обработки транзакционных мутаций кэша.
 * Отвечает за атомарное выполнение операций Insert, Update, Delete и координацию Observer-событий.
 */
public final class XdStorageCacheMutationEngine {

    private static final Logger log = LogManager.getLogger(XdStorageCacheMutationEngine.class);

    private XdStorageCacheMutationEngine() {
        // Утилитный класс-движок, запрет инстанцирования
    }

    /**
     * Выполняет атомарную вставку объекта в транзакционный контекст кэша.
     */
    public static void executeInsert(
            final Object newObject,
            final XdStorageTransaction transaction,
            final Map<Object, XdStorageCacheVersionRecord> cacheRegistry,
            final Map<String, Map<Object, XdStorageCacheVersionRecord>> changesRegistry,
            final Map<String, Map<Object, IXdStorageIdObservableWrapper>> changesUnidentified,
            final Map<String, Map<Object, Object>> readObjectsRegistry,
            final XdStorageObjectIdField field,
            final IXdStorageCloner cloner) throws XdStorageException {

        final String txId = transaction.getTransactionId();

        // Граничное условие: если объект является наблюдаемым прокси без ID
        if (IXdStorageIdObservableWrapper.class.isAssignableFrom(newObject.getClass())) {
            final Map<Object, IXdStorageIdObservableWrapper> records = changesUnidentified
                    .computeIfAbsent(txId, id -> new ConcurrentHashMap<>());
            records.put(newObject, (IXdStorageIdObservableWrapper) newObject);

            // Атомарный слушатель: сработает лениво, когда СУБД сгенерирует ID для прокси
            ((IXdStorageIdObservableWrapper) newObject).addObserver(new XdStorageAbstractIdObserver() {
                @Override
                public void onNewIdIsSet(final IXdStorageIdObservableWrapper wrapper, final Object id) {
                    final Map<Object, IXdStorageIdObservableWrapper> wrappers = changesUnidentified.get(txId);
                    if (wrappers != null) {
                        wrappers.remove(newObject);
                    }

                    final Object rawObject = XdStorageObjectUtils.getWrappedObjectOrSameObject(newObject);
                    try {
                        XdStorageCacheVersionRecord record = cacheRegistry.computeIfAbsent(id, keyId -> {
                            XdStorageCacheVersionRecord r = new XdStorageCacheVersionRecord();
                            r.id = id;
                            r.object = null;
                            r.newObject = cloner.unwrapAndClone(rawObject);
                            r.change = XdStorageResourceCache.Change.insert;
                            return r;
                        });

                        record.lock(transaction);
                        if (!record.canBeChangedByTransaction(transaction)) {
                            throw new XdStorageRuntimeException("Конкурентный конфликт: объект ID " + id + " заблокирован другой транзакцией.");
                        }

                        final Map<Object, Object> readObjects = readObjectsRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>());
                        readObjects.put(id, rawObject);

                        changesRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>()).put(id, record);
                    } catch (final XdStorageException e) {
                        throw new XdStorageRuntimeException("Критический сбой ленивой фиксации ID в кэше", e);
                    }
                }
            });
        } else {
            // Стандартный сценарий: объект имеет явный заполненный ID
            final Object objectId = field.get(newObject);
            if (objectId == null) {
                throw new XdStorageException("Запрещено сохранять объект с null идентификатором.");
            }

            final Map<Object, Object> readObjects = readObjectsRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>());

            XdStorageCacheVersionRecord record = cacheRegistry.computeIfAbsent(objectId, id -> {
                XdStorageCacheVersionRecord r = new XdStorageCacheVersionRecord();
                r.id = objectId;
                r.object = null;
                r.newObject = cloner.unwrapAndClone(newObject);
                r.change = XdStorageResourceCache.Change.insert;
                return r;
            });

            record.lock(transaction);
            if (!record.canBeChangedByTransaction(transaction) || record.isChangedByTransaction(transaction)) {
                throw new XdStorageException("Объект с ID " + objectId + " уже изменен или заблокирован в параллельной транзакции.");
            }

            readObjects.put(objectId, newObject);
            changesRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>()).put(objectId, record);
        }
    }

    /**
     * Выполняет атомарное обновление объекта в транзакционном контексте кэша.
     */
    public static void executeUpdate(
            final Object newObject,
            final XdStorageTransaction transaction,
            final Map<Object, XdStorageCacheVersionRecord> cacheRegistry,
            final Map<String, Map<Object, XdStorageCacheVersionRecord>> changesRegistry,
            final Map<String, Map<Object, Object>> readObjectsRegistry,
            final XdStorageObjectIdField field,
            final IXdStorageCloner cloner) throws XdStorageException {

        final String txId = transaction.getTransactionId();
        final Object objectId = field.get(newObject);

        XdStorageCacheVersionRecord record = cacheRegistry.get(objectId);
        if (record == null) {
            throw new XdStorageException("Объект с ID " + objectId + " не существует в базе данных и не может быть обновлен.");
        }

        record.lock(transaction);
        final Map<Object, Object> readObjects = readObjectsRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>());

        if (record.isReadChange()) {
            record.newObject = cloner.unwrapAndClone(newObject);
            record.markUpdate();
            readObjects.put(objectId, newObject);
        } else if (record.isChangedByTransaction(transaction)) {
            if (record.isUpdateChange() || record.isInsertChange()) {
                record.newObject = cloner.unwrapAndClone(newObject);
                readObjects.put(objectId, newObject);
            } else {
                throw new XdStorageException("Попытка обновить объект ID " + objectId + ", который уже удален в текущей транзакции.");
            }
        } else {
            throw new XdStorageException("Конкурентная модификация: объект ID " + objectId + " изменен параллельной транзакцией.");
        }

        changesRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>()).put(objectId, record);
    }

    /**
     * Выполняет атомарное удаление объекта из транзакционного контекста кэша.
     */
    public static void executeDelete(
            final Object objectId,
            final XdStorageTransaction transaction,
            final Map<Object, XdStorageCacheVersionRecord> cacheRegistry,
            final Map<String, Map<Object, XdStorageCacheVersionRecord>> changesRegistry,
            final Map<String, Map<Object, Object>> readObjectsRegistry) throws XdStorageException {

        final String txId = transaction.getTransactionId();
        XdStorageCacheVersionRecord record = cacheRegistry.get(objectId);

        if (record == null) {
            throw new XdStorageException("Объект с ID " + objectId + " не существует и не может быть удален.");
        }

        record.lock(transaction);
        final Map<Object, Object> readObjects = readObjectsRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>());

        if (record.isReadChange()) {
            record.newObject = null;
            record.markDelete();
            readObjects.remove(objectId);
        } else if (record.isChangedByTransaction(transaction)) {
            if (record.isInsertChange()) {
                cacheRegistry.remove(objectId); // Если объект был вставлен и тут же удален в одной TX — стираем полностью
                readObjects.remove(objectId);
            } else if (record.isUpdateChange()) {
                record.newObject = null;
                record.markDelete();
                readObjects.remove(objectId);
            } else {
                throw new XdStorageException("Попытка повторного удаления объекта с ID " + objectId);
            }
        } else {
            throw new XdStorageException("Конкурентная модификация: объект ID " + objectId + " заблокирован другой транзакцией.");
        }

        changesRegistry.computeIfAbsent(txId, k -> new ConcurrentHashMap<>()).put(objectId, record);
    }
}
