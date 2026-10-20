package org.flib.xdstorage.resource.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Протокол завершения транзакции кэша ресурсов: двухфазный коммит (prepare/perform),
 * финализация, откат по журналу мутаций и откат упавшего коммита (вынесен из
 * XdStorageResourceCache при разделении класса).
 */
public final class XdStorageCacheCommitProtocol {

    private static final Logger log = LogManager.getLogger(XdStorageCacheCommitProtocol.class);

    private final XdStorageCacheContext cache;

    public XdStorageCacheCommitProtocol(final XdStorageCacheContext cache) {
        this.cache = cache;
    }

    public void prepareCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        final String transactionId = transaction.getTransactionId();

        final Map<Object, XdStorageCacheObjectChange> readObjects = cache.workspace().readObjectsOf(transaction);

        final Map<Object, XdStorageCacheRecord> map = cache.workspace().changes(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, XdStorageCacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final XdStorageCacheRecord record = it.next().getValue();
                Object oldObject = null, newObject = null;
                XdStorageObjectOperationType type = null;
                synchronized (record) {
                    if (record.isInsertChange()) {
                        newObject = record.getNewObject();
                        type = XdStorageObjectOperationType.Insert;

                        if (newObject != null) {
                            Object insertId = cache.idField().get(newObject);
                            if (insertId != null && readObjects != null && readObjects.containsKey(insertId)) {
                                newObject = readObjects.get(insertId).object;
                            }
                        }
                    } else if (record.isUpdateChange()) {
                        oldObject = record.getObject();
                        type = XdStorageObjectOperationType.Update;

                        if (oldObject != null) {
                            Object updateId = cache.idField().get(oldObject);
                            if (updateId != null && readObjects != null && readObjects.containsKey(updateId)) {
                                newObject = readObjects.get(updateId).object;
                            }
                        }
                    } else if (record.isDeleteChange()) {
                        oldObject = record.getObject();
                        type = XdStorageObjectOperationType.Delete;
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("PREPARE COMMIT " + type + " by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + oldObject + "\r\n NEW OBJECT: " + newObject);
                    }

                    collector.addChangeObject(type, record.getId(),
                            oldObject != null ? XdStorageObjectUtils.cloneObject(oldObject) : null,
                            newObject != null ? newObject : null);

                    record.prepareCommit();
                }
            }
        }

        final Map<Object, IXdStorageIdObservableWrapper> wrappers = cache.workspace().unidentified(transactionId);
        if (wrappers != null) {
            for (final IXdStorageIdObservableWrapper wrapper : wrappers.values()) {
                collector.addChangeObject(XdStorageObjectOperationType.Insert, null, null, wrapper);
            }
        }
    }

    public void performCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges collector) {
        final String transactionId = transaction.getTransactionId();

        final Map<Object, XdStorageCacheObjectChange> readObjects = cache.workspace().readObjectsOf(transaction);

        final Map<Object, XdStorageCacheRecord> map = cache.workspace().changes(transactionId);
        if (map != null) {
            final Iterator<Entry<Object, XdStorageCacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final XdStorageCacheRecord record = it.next().getValue();
                Object oldObject = null, newObject = null;
                XdStorageObjectOperationType type = null;
                synchronized (record) {
                    if (record.isInsertChange()) {
                        newObject = record.getNewObject();
                        type = XdStorageObjectOperationType.Insert;

                        if (newObject != null) {
                            Object commitId = cache.idField().get(newObject);
                            if (commitId != null && readObjects != null && readObjects.containsKey(commitId)) {
                                newObject = readObjects.get(commitId).object;
                            }
                        }
                    } else if (record.isUpdateChange()) {
                        oldObject = record.getObject();
                        type = XdStorageObjectOperationType.Update;

                        if (oldObject != null) {
                            Object commitId = cache.idField().get(oldObject);
                            if (commitId != null && readObjects != null && readObjects.containsKey(commitId)) {
                                newObject = readObjects.get(commitId).object;
                            }
                        }
                    } else if (record.isDeleteChange()) {
                        oldObject = record.getObject();
                        type = XdStorageObjectOperationType.Delete;
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("PERFORM COMMIT " + type + " by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + oldObject + "\r\n NEW OBJECT: " + newObject);
                    }

                    collector.addChangeObject(type, record.getId(),
                            oldObject != null ? XdStorageObjectUtils.cloneObject(oldObject) : null,
                            newObject != null ? newObject : null);

                    record.performCommit();
                }
            }
        }

        final Map<Object, IXdStorageIdObservableWrapper> wrappers = cache.workspace().unidentified(transactionId);
        if (wrappers != null) {
            for (final IXdStorageIdObservableWrapper wrapper : wrappers.values()) {
                collector.addChangeObject(XdStorageObjectOperationType.Insert, null, null, wrapper);
            }
        }
    }

    public void commit(final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        cache.workspace().removeUndoLog(transactionId); // изменения стали постоянными — журнал отката не нужен
        final Map<Object, XdStorageCacheRecord> map = cache.workspace().removeChanges(transactionId);
        if (map != null) {
            log.info("commit of tx {}: {} record(s) became permanent", transactionId, map.size());
            final Iterator<Entry<Object, XdStorageCacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final XdStorageCacheRecord record = it.next().getValue();

                if (log.isDebugEnabled()) {
                    log.debug("COMMIT by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                }

                boolean committedNow = true;
                synchronized (record) {
                    try {
                        record.commit(transaction);
                    } catch (final XdStorageRuntimeException illegalState) {
                        // R-58: запись УЖЕ ЗАКОММИЧЕНА (повторный commit той же записи —
                        // вложенный/двойной контур коммита: флаги фаз потреблены первым
                        // проходом, state=committed). Пропускаем с WARN: данные уже
                        // постоянны, повторная фиксация ничего не меняет (и не захватывает
                        // latch повторно — unlock не нужен). Для записей в иных состояниях
                        // ошибка остается громкой.
                        if (record.isCommitedState()) {
                            log.warn("commit of tx {}: record {} already committed (double-commit path) — skipped",
                                    transactionId, record.getId());
                            committedNow = false;
                        } else {
                            throw illegalState;
                        }
                    }
                }

                if (committedNow) {
                    record.unlock(transaction);
                }
            }
        }

        cache.workspace().finishTransaction(transactionId);
    }

    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        final Map<Object, XdStorageCacheRecord> map = cache.workspace().removeChanges(transactionId);
        final java.util.Deque<XdStorageCacheRecord> undoLog = cache.workspace().removeUndoLog(transactionId);

        // UNDO-CHANGE-LOG: проигрываем журнал мутаций В ОБРАТНОМ порядке — каждая мутация
        // имеет ровно одну точку отмены, и откат строго инвертирует хронологию транзакции.
        if (undoLog != null) {
            final java.util.Iterator<XdStorageCacheRecord> it = undoLog.descendingIterator();
            while (it.hasNext()) {
                final XdStorageCacheRecord record = it.next();

                if (log.isDebugEnabled()) {
                    log.debug("ROLLBACK by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                }

                // Фиксируем признак вставки строго по транзакционному контракту СУБД
                boolean isInsertOp = record.isInsertChange() && record.isChangedByTransaction(transaction);
                Object recordId = record.getId();

                try {
                    synchronized (record) {
                        if (isInsertOp && cache.isInfrastructureCache()) {
                            record.rollbackInsertKeepServing();
                        } else {
                            record.rollback();
                        }
                    }

                    if (isInsertOp && !cache.isInfrastructureCache() && recordId != null) {
                        cache.records().remove(recordId);
                    }
                    record.unlock(transaction);
                } catch (final RuntimeException e) {
                    // Откат не завершился: оставшиеся записи журнала содержат незакрытые мутации.
                    // Без этого ERROR половина отката была бы НЕЗаметной.
                    log.error("ROLLBACK FAILED mid-journal for transaction {} on record {} — {} undo entries left unprocessed",
                            transactionId, recordId, countRemaining(it), e);
                    throw e;
                }
            }
        } else if (map != null) {
            // Журнал отсутствует (транзакция из старого кода/мока) — прежний порядок по карте
            final Iterator<Entry<Object, XdStorageCacheRecord>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                final XdStorageCacheRecord record = it.next().getValue();
                boolean isInsertOp = record.isInsertChange() && record.isChangedByTransaction(transaction);
                Object recordId = record.getId();
                synchronized (record) {
                    if (isInsertOp && cache.isInfrastructureCache()) {
                        record.rollbackInsertKeepServing();
                    } else {
                        record.rollback();
                    }
                }
                if (isInsertOp && !cache.isInfrastructureCache() && recordId != null) {
                    cache.records().remove(recordId);
                }
                record.unlock(transaction);
            }
        }

        if (map != null || undoLog != null) {
            log.info("rollback of tx {}: {} record(s) reverted in reverse mutation order",
                    transactionId, map != null ? map.size() : 0);
        }

        cache.workspace().finishTransaction(transactionId);
    }

    public void rollbackFailedCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> committed) throws XdStorageException {
        final String transactionId = transaction.getTransactionId();
        cache.workspace().removeUndoLog(transactionId);
        final Map<Object, XdStorageCacheRecord> map = cache.workspace().removeChanges(transactionId);
        if (map != null) {
            for (final XdStorageObjectChange change : committed) {
                if (change.oldObject == null && change.newObject == null) {
                    continue;
                }
                final Object object = change.oldObject;
                final Object id = cache.idField().get(object != null ? object : change.newObject);
                XdStorageCacheRecord record = cache.records().get(id), oldRecord = map.get(id);
                if (record == null && object != null) { // was deleted by this transaction
                    cache.records().put(id, record = oldRecord != null ? oldRecord : cache.createReadRecord(object));
                } else if (object == null) { // was inserted by this transaction
                    record = cache.records().remove(id);
                } else { // was updated by this transaction
                    cache.records().put(id, record = oldRecord != null ? oldRecord : cache.createReadRecord(object));
                }
                if (record != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("ROLLBACK FAILED COMMIT by transaction " + transaction.getTransactionId() + "\r\n OLD OBJECT: " + record.getObject() + "\r\n NEW OBJECT: " + record.getNewObject());
                    }

                    synchronized (record) {
                        record.rollback();
                    }
                    record.unlock(transaction);
                }
            }
        }

        if (map != null) {
            log.info("rollback-failed-commit of tx {}: {} record(s) restored from the collector's committed changes", transactionId, map.size());
        }

        cache.workspace().finishTransaction(transactionId);
    }

    private static int countRemaining(final java.util.Iterator<?> it) {
        int n = 0;
        while (it.hasNext()) {
            it.next();
            ++n;
        }
        return n;
    }
}
