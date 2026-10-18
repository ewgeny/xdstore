package org.flib.xdstorage.resource.cache;

import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.transaction.XdStorageTransaction;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Транзакционные контуры кэша ресурсов — все состояния, живущие ПОКА жива транзакция,
 * сгруппированы в одном месте (вынесены из XdStorageResourceCache при разделении класса):
 * <ul>
 *   <li>changes — write-set транзакции (id -> запись кэша); источник hasChanges/2PC;</li>
 *   <li>undoLogs — ЖУРНАЛ ОТКАТА (undo-change-log): записи кэша в ПОРЯДКЕ первой мутации
 *       транзакцией. rollback() проигрывает журнал в ОБРАТНОМ порядке — это дает полный
 *       контроль над откатом (каждая мутация имеет ровно одну точку отмены) и
 *       диагностическую картину "что именно делала транзакция" при сбое отката.
 *       Очищается при commit (изменения стали постоянными), rollback и clear;</li>
 *   <li>unidentified — неидентифицированные observable-обертки (объект еще без DB-id);</li>
 *   <li>readObjects — материализованные (прочитанные/транзитные) объекты транзакции.</li>
 * </ul>
 * Все карты — ConcurrentHashMap, ключ — id транзакции; сам объект не добавляет
 * синхронизации сверх той, что была у карт в XdStorageResourceCache.
 */
public final class XdStorageTransactionWorkspace {

    private final Map<String, Map<Object, XdStorageCacheRecord>> changes = new ConcurrentHashMap<>();

    private final Map<String, Deque<XdStorageCacheRecord>> undoLogs = new ConcurrentHashMap<>();

    private final Map<String, Map<Object, IXdStorageIdObservableWrapper>> unidentified = new ConcurrentHashMap<>();

    private final Map<String, Map<Object, XdStorageCacheObjectChange>> readObjects = new ConcurrentHashMap<>();

    /** Write-set транзакции (без создания): null, если транзакция ничего не меняла. */
    public Map<Object, XdStorageCacheRecord> changes(final String transactionId) {
        return changes.get(transactionId);
    }

    /** Write-set транзакции (с ленивым созданием). */
    private Map<Object, XdStorageCacheRecord> changesOf(final String transactionId) {
        Map<Object, XdStorageCacheRecord> map = changes.get(transactionId);
        if (map == null) {
            changes.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            map = changes.get(transactionId);
        }
        return map;
    }

    /** Журнал отката транзакции (создается при первой регистрации мутации). */
    Deque<XdStorageCacheRecord> undoLog(final String transactionId) {
        return undoLogs.computeIfAbsent(transactionId, k -> new ArrayDeque<>());
    }

    /** Журнал отката (без создания): null, если транзакция ничего не мутировала. */
    Deque<XdStorageCacheRecord> peekUndoLog(final String transactionId) {
        return undoLogs.get(transactionId);
    }

    /** Неидентифицированные observable-обертки (без создания): null, если нет. */
    public Map<Object, IXdStorageIdObservableWrapper> unidentified(final String transactionId) {
        return unidentified.get(transactionId);
    }

    /** Неидентифицированные observable-обертки (с ленивым созданием). */
    public Map<Object, IXdStorageIdObservableWrapper> unidentifiedOf(final String transactionId) {
        Map<Object, IXdStorageIdObservableWrapper> records = unidentified.get(transactionId);
        if (records == null) {
            unidentified.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            records = unidentified.get(transactionId);
        }
        return records;
    }

    /** Материализованные объекты транзакции (без создания): null, если нет. */
    public Map<Object, XdStorageCacheObjectChange> readObjects(final String transactionId) {
        return readObjects.get(transactionId);
    }

    /** Материализованные объекты транзакции (с ленивым созданием). */
    public Map<Object, XdStorageCacheObjectChange> readObjectsOf(final XdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();
        Map<Object, XdStorageCacheObjectChange> transactionReadObjects = readObjects.get(transactionId);
        if (transactionReadObjects == null) {
            readObjects.putIfAbsent(transactionId, new ConcurrentHashMap<>());
            transactionReadObjects = readObjects.get(transactionId);
        }
        return transactionReadObjects;
    }

    /**
     * Регистрация мутации записи в write-set транзакции. Первая мутация записи
     * попадает в журнал отката (каждая запись — ровно один вход в журнал).
     */
    public void registerMutation(final String transactionId, final Object objectId, final XdStorageCacheRecord record) {
        final Map<Object, XdStorageCacheRecord> map = changesOf(transactionId);
        if (map.put(objectId, record) == null) {
            undoLog(transactionId).addLast(record); // первая мутация записи — в журнал отката
        }
    }

    /** Есть ли у транзакции незакоммиченные мутации (write-set или неидентифицированные). */
    public boolean hasChanges(final String transactionId) {
        final Map<Object, XdStorageCacheRecord> transactionChanges = changes.get(transactionId);
        final Map<Object, IXdStorageIdObservableWrapper> transactionUnidentifiedObjects = unidentified.get(transactionId);
        return (transactionChanges != null && !transactionChanges.isEmpty()) ||
                (transactionUnidentifiedObjects != null && !transactionUnidentifiedObjects.isEmpty());
    }

    /** Изъять write-set (при commit/rollback/rollbackFailedCommit). */
    public Map<Object, XdStorageCacheRecord> removeChanges(final String transactionId) {
        return changes.remove(transactionId);
    }

    /** Изъять журнал отката (при commit/rollback/rollbackFailedCommit). */
    public Deque<XdStorageCacheRecord> removeUndoLog(final String transactionId) {
        return undoLogs.remove(transactionId);
    }

    /** Полная очистка транзакционного контура (cache.clear). */
    public void clear(final String transactionId) {
        readObjects.remove(transactionId);
        unidentified.remove(transactionId);
        changes.remove(transactionId);
        undoLogs.remove(transactionId);
    }

    /** Завершение коммита/отката: транзитные и неидентифицированные объекты больше не нужны. */
    public void finishTransaction(final String transactionId) {
        unidentified.remove(transactionId);
        readObjects.remove(transactionId);
    }
}
