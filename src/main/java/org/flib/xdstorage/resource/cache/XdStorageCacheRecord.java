package org.flib.xdstorage.resource.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.XdStorageIsolationLevel;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Запись кэша ресурсов: MVCC-состояние одного объекта (id -> версии) — государственная
 * машина read/insert/update/delete x undefined/locked/committed, record-level latch,
 * протокол двухфазного коммита и вычисление ВИДИМОЙ версии объекта для транзакции.
 * Вынесена из XdStorageResourceCache при разделении класса (ранее — приватный
 * внутренний класс CacheRecord). Все мутации состояния выполняются под монитором
 * самой записи (synchronized (record) на стороне вызывающих).
 */
public final class XdStorageCacheRecord {

    private static final Logger log = LogManager.getLogger(XdStorageCacheRecord.class);

    /** Режим вычисления видимой версии: различия сайтов чтения сосредоточены здесь. */
    public enum View {
        /** Сканы (предикат/полный/watch/readByReference): базовый MVCC-переключатель. */
        SCAN,
        /** Чтение по id: + барьер чужого коммита (committingSerial) и prevObject-фолбэк (R-45). */
        BY_ID,
        /** Выписка на диск (readUnsafe): последнее закоммиченное состояние для чужих записей. */
        WRITE_OUT
    }

    private enum State {
        undefined, locked, committed, rolledback
    }

    private final XdStorageCacheContext cache;

    private Object id;

    private Object prevObject;

    private Object object;

    private Object newObject;

    private XdStorageTransaction transaction;

    private long timestamp = Long.MIN_VALUE;

    private long prevTimestamp = Long.MIN_VALUE;

    private XdStorageCacheChange change;

    private XdStorageCacheChange previousChange;

    private State state;

    private State previousState;

    private boolean firstPhaseCommit;

    private boolean secondPhaseCommit;

    private AtomicBoolean locked = new AtomicBoolean(false);

    /** Версия записи: растет при каждой мутации состояния (для RC-освежения чтений). */
    private volatile long version;

    /**
     * Номер фактического коммита текущей версии (граница видимости SNAPSHOT-читателей).
     * 0 = версия пришла с диска/еще не коммитилась в памяти — видна всем читателям.
     */
    private volatile long commitSerial;

    private Map<String, Long> blockingTime = new HashMap<>();

    XdStorageCacheRecord(final XdStorageCacheContext cache) {
        this.cache = cache;
    }

    /** Запись чистого чтения (объект пришел с диска / из живой структуры). */
    public static XdStorageCacheRecord newReadRecord(final XdStorageCacheContext cache, final XdStorageObjectIdField field, final Object object) {
        final XdStorageCacheRecord record = new XdStorageCacheRecord(cache);
        record.id = field.get(object);
        record.object = object;
        record.newObject = null;
        record.transaction = null;
        record.change = XdStorageCacheChange.read;
        record.state = State.undefined;
        return record;
    }

    /** Запись незакоммиченной вставки: контент в newObject, object == null до коммита. */
    public static XdStorageCacheRecord newInsertRecord(final XdStorageCacheContext cache, final XdStorageObjectIdField field, final Object newObject, final XdStorageTransaction transaction) {
        final XdStorageCacheRecord record = new XdStorageCacheRecord(cache);
        record.id = field.get(newObject);
        record.object = null;
        record.newObject = newObject;
        record.transaction = transaction;
        record.change = XdStorageCacheChange.insert;
        record.timestamp = transaction.getTimestart();
        record.state = State.locked;
        return record;
    }

    public Object getId() {
        return this.id;
    }

    public long getVersion() {
        return version;
    }

    /** R-40 диагностика отказов: состояние записи на момент отказа обновления. */
    public String getState() {
        return String.valueOf(state);
    }

    /** R-40 диагностика отказов: id транзакции-владельца записи (или null). */
    public String getOwnerTransactionId() {
        return transaction != null ? transaction.getTransactionId() : null;
    }

    /** Владелец записи (транзакция, захватившая ее последней) или null. */
    public XdStorageTransaction getOwnerTransaction() {
        return transaction;
    }

    /** R-40 диагностика отказов: timestamp записи на момент отказа обновления. */
    public long getRecordTimestamp() {
        return timestamp;
    }

    public boolean canBeChangedByTransaction(final XdStorageTransaction transaction) {
        boolean isOwnerTxAlive = this.transaction != null &&
                cache.isTransactionAlive(this.transaction);

        // =========================================================================
        // R-40: РЕЛАКСАЦИЯ ЦЕПОЧКИ ВЛОЖЕННОСТИ. Nested-транзакция — тот же логический
        // писатель, что и ее родитель: спуск parent-tx по индексу обновляет родительские
        // указатели узлов (record уходит в locked до коммита parent), после чего спуск
        // nested-tx по ТОМУ ЖЕ дереву получал отказ "can not be updated" на СВОЮ ЖЕ
        // блокировку — детерминированный ноль прогрессов под nested-стрессом (R-37).
        // Идентичность цепочки строгая (по ссылкам транзакций), чужие воркеры не
        // пересекаются никогда.
        // =========================================================================
        boolean result = this.state == State.undefined
                || !isOwnerTxAlive
                || (this.state == State.locked && this.transaction == transaction && transaction.getTimestart() == timestamp)
                || (this.state == State.locked && isOwnedByTransactionChain(transaction))
                || (this.state == State.committed && transaction.getTimestart() > timestamp);

        if (log.isDebugEnabled()) {
            log.debug("canBeChangedByTransaction state: " + this.state
                    + " \r\n this.transaction: " + this.transaction + "(" + (this.transaction != null ? this.transaction.getTransactionId() : null)
                    + ")\r\n transaction: " + transaction + "(" + transaction.getTransactionId()
                    + ")\r\n this.timestamp: " + timestamp + "\r\n tran.timestart: " + transaction.getTimestart());
        }

        return result;
    }

    /**
     * R-40: true, если владелец записи — звено цепочки вложенности вызывающей
     * транзакции (сам вызывающий, его parent, grandparent, ...).
     */
    private boolean isOwnedByTransactionChain(final XdStorageTransaction caller) {
        if (this.transaction == null) {
            return false;
        }
        for (XdStorageTransaction t = caller; t != null; t = t.getGlobalTransaction()) {
            if (t == this.transaction) {
                return true;
            }
        }
        return false;
    }

    public boolean canBeChangedByTransaction(final XdStorageTransaction transaction, final long readObjectByTransactionTimestamp) {
        boolean isOwnerTxAlive = this.transaction != null &&
                cache.isTransactionAlive(this.transaction);

        boolean result = this.state == State.undefined
                || !isOwnerTxAlive
                || (this.state == State.locked && this.transaction == transaction && transaction.getTimestart() == timestamp)
                || (this.state == State.locked && isOwnedByTransactionChain(transaction))
                || (this.state == State.committed && readObjectByTransactionTimestamp > timestamp);

        if (log.isDebugEnabled()) {
            log.debug("canBeChangedByTransaction state: " + this.state
                    + " \r\n this.transaction: " + this.transaction + "(" + (this.transaction != null ? this.transaction.getTransactionId() : null)
                    + ")\r\n transaction: " + transaction + "(" + transaction.getTransactionId()
                    + ")\r\n this.timestamp: " + timestamp + "\r\n tran.timestart: " + readObjectByTransactionTimestamp);
        }

        return result;
    }

    public boolean isChangedByTransaction(final XdStorageTransaction transaction) {
        return this.change != XdStorageCacheChange.read && this.transaction != null && this.transaction == transaction
                && transaction.getTimestart() == timestamp;
    }

    public Object getObject() {
        return this.object;
    }

    public Object getNewObject() {
        return this.newObject;
    }

    public void setNewObject(final Object newObject) {
        this.newObject = newObject;
    }

    public boolean isCommitedState() {
        return this.state == State.committed;
    }

    public boolean isReadChange() {
        return this.change == XdStorageCacheChange.read;
    }

    public boolean isInsertChange() {
        // Было: return this.change == Change.insert; (но из-за опечаток в CHM проверялся Change.change)
        // Стало: Прямое, строгое и кристальное вычисление типа транзакционной вставки!
        return this.change == XdStorageCacheChange.insert;
    }

    public boolean isUpdateChange() {
        return this.change == XdStorageCacheChange.update;
    }

    public boolean isDeleteChange() {
        return this.change == XdStorageCacheChange.delete;
    }

    /**
     * ВИДИМАЯ ВЕРСИЯ ОБЪЕКТА для транзакции — консолидированный MVCC-переключатель
     * шести сайтов чтения кэша (скан по предикату, watch, полный скан, unsafe-скан,
     * чтение по id, чтение по ссылке). Различия сайтов сведены к параметрам view
     * и committingSerial; сама выборка обязана выполняться под монитором записи.
     */
    public Object resolveVisible(final XdStorageTransaction transaction, final View view, final long committingSerial) {
        if (isUpdateChange()) {
            if (isCommitedState()) {
                return object;
            }
            if (isChangedByTransaction(transaction)) {
                return newObject;
            }
            if (view == View.BY_ID) {
                // R-45: чужая транзакция В ФАЗЕ КОММИТА с serial внутри снапшота читателя —
                // новое состояние обязано быть видно атомарно (все ресурсы коммита разом);
                // иначе чужой читатель видит последнюю ЗАКОММИЧЕННУЮ версию (prevObject):
                // живой record.object был бы состоянием чужой НЕЗАКОММИЧЕННОЙ транзакции.
                if (committingSerial != 0) {
                    return newObject != null ? newObject : object;
                }
                if (canBeChangedByTransaction(transaction)) {
                    return object;
                }
                return prevObject != null ? prevObject : object;
            }
            return object;
        }
        if (isInsertChange()) {
            if (isCommitedState()) {
                return object;
            }
            if (isChangedByTransaction(transaction)) {
                return newObject;
            }
            if (view == View.BY_ID && committingSerial != 0) {
                return newObject;
            }
            return null;
        }
        if (isDeleteChange()) {
            if (!isCommitedState()) {
                return !isChangedByTransaction(transaction) ? object : null;
            }
            return servesSnapshotVersion(transaction) ? prevObject : null;
        }
        // WRITE-OUT (коммит/откат ресурса): для чужих закоммиченных записей берем
        // ПОСЛЕДНЕЕ закоммиченное состояние, а не снапшотную route'ировку — иначе
        // коммиттер, начавший раньше соседа, перезаписал бы файл его старой версией
        return view == View.WRITE_OUT ? object : getObjectForTransaction(transaction);
    }

    public void markUpdate(IXdStorageTransaction transaction) {
        ++version;
        this.prevObject = this.object;

        // =========================================================================
        // СУБД ТАЙМСТAМП-БAРЬEР: Защищаем историческую сетку времени при холодном старте!
        // Перезаписываем prevTimestamp только если у ноды есть реальное время коммита.
        // Это полностью предотвращает превращение timestamp в Long.MIN_VALUE при параллельных
        // конфликтах сессий, возвращая выборку к исходным 1000 элементам!
        // =========================================================================
        if (this.timestamp != Long.MIN_VALUE) {
            this.prevTimestamp = this.timestamp;
        }
        this.timestamp = transaction.getTimestart();

        this.previousChange = this.change;
        this.change = XdStorageCacheChange.update;
        this.previousState = this.state;
        this.state = State.locked;
    }

    public void markDelete(IXdStorageTransaction transaction) {
        ++version;
        this.prevObject = this.object;

        // Симметрично защищаем временную сетку для операций удаления
        if (this.timestamp != Long.MIN_VALUE) {
            this.prevTimestamp = this.timestamp;
        }
        this.timestamp = transaction.getTimestart();

        this.previousChange = this.change;
        this.change = XdStorageCacheChange.delete;
        this.previousState = this.state;
        this.state = State.locked;
    }

    public void lock(final XdStorageTransaction transaction) throws XdStorageException {
        // R-27: инфраструктурные записи (деревья/узлы) НЕ захватывают latch. Структурная
        // конкуренция уже сериализована write-локом самого дерева; удержание latch-а до
        // конца транзакции-держателя на горячих узлах давало "locked for update" таймауты
        // при 25 воркерах. Своя-мутация определяется по record.transaction, проставленному
        // в createInsertRecord/markUpdate.
        if (cache.isInfrastructureCache()) {
            if (this.transaction == null) {
                this.transaction = transaction;
            }
            return;
        }
        boolean isOwnerAlive = this.transaction != null &&
                cache.isTransactionAlive(this.transaction);

        if (this.transaction != null && !isOwnerAlive && this.transaction != transaction) {
            log.debug("record {} adopted from dead transaction {} by {}",
                    id, this.transaction.getTransactionId(), transaction.getTransactionId());
            this.transaction = null;
            synchronized (locked) {
                locked.set(false);
                locked.notify();
            }
        }

        if (this.transaction == null || this.transaction != transaction) {
            synchronized (locked) {
                while (locked.get()) {
                    try {
                        final String transactionId = transaction.getTransactionId();

                        boolean currentOwnerAlive = this.transaction != null &&
                                cache.isTransactionAlive(this.transaction);
                        if (this.transaction != null && !currentOwnerAlive) {
                            log.debug("record {} owner {} died while waiting — adopted by {}",
                                    id, this.transaction.getTransactionId(), transactionId);
                            this.transaction = null;
                            locked.set(false);
                            break;
                        }

                        final Long startTime = blockingTime.remove(transactionId);
                        final Long currentTime = System.nanoTime();
                        if (startTime == null) {
                            blockingTime.put(transactionId, currentTime);
                        } else if ((currentTime - startTime) >= transaction.getTimeout() * 1_000_000) {
                            log.warn("record {} locked by transaction {} (change={}) — waiter {} timed out after {} ms",
                                    id, this.transaction != null ? this.transaction.getTransactionId() : null,
                                    change, transactionId, transaction.getTimeout());
                            throw new XdStorageException("transaction " + transactionId + " should be rolled back by timeout. Object with id " + id + " is locked for " + change);
                        } else {
                            blockingTime.put(transactionId, startTime);
                        }

                        locked.wait(transaction.getTimeout() / 5);
                    } catch (final InterruptedException e) {
                        throw new XdStorageException("locking object interrupted", e);
                    }
                }
                locked.set(true);
                // Phase D: запись о времени ожидания больше не нужна после захвата —
                // иначе на записи течет по одному entry на каждую ждавшую транзакцию
                blockingTime.remove(transaction.getTransactionId());
            }
            this.transaction = transaction;
        }
    }

    public void unlock(final XdStorageTransaction transaction) throws XdStorageException {
        // R-27: инфраструктурные записи не держат latch — просто разъединяем владельца
        if (cache.isInfrastructureCache()) {
            if (this.transaction == transaction) {
                this.transaction = null;
            }
            return;
        }
        if (this.transaction == transaction) {
            this.transaction = null;
            synchronized (locked) {
                locked.set(false);
                locked.notify();
            }
        } else {
            log.warn("unlock of record {} rejected: caller {} does not own it (owner={})",
                    id, transaction.getTransactionId(), this.transaction != null ? this.transaction.getTransactionId() : null);
            throw new XdStorageException("trying to release locking object by the transaction is not locked this object");
        }
    }

    public void prepareCommit() {
        firstPhaseCommit = true;
    }

    public void performCommit() {
        secondPhaseCommit = true;
    }

    public void commit(IXdStorageTransaction tx) {
        ++version;
        if (!firstPhaseCommit || !secondPhaseCommit) {
            log.error("commit of record {} rejected: firstPhaseCommit={}, secondPhaseCommit={}, state={}, change={}, owner={}",
                    id, firstPhaseCommit, secondPhaseCommit, state, change,
                    transaction != null ? transaction.getTransactionId() : null);
            throw new XdStorageRuntimeException("Illegal state of record to commit");
        }

        this.prevObject = this.object;
        this.object = this.newObject;
        this.newObject = null;
        this.state = State.committed;
        this.previousState = State.undefined;

        firstPhaseCommit = false;
        secondPhaseCommit = false;

        this.prevTimestamp = this.timestamp;
        this.timestamp = tx.getTimestart();

        // НОМЕР ФАКТИЧЕСКОГО КОММИТА: граница видимости снапшотных читателей.
        // timestart владельца (метится при НАЧАЛЕ его транзакции) лжет читателям,
        // начавшим в окне [начало владельца, коммит]: версия еще не была видна,
        // когда они стартовали. R-45: ВСЕ записи транзакции получают ОДИН serial,
        // зарезервированный в начале коммита — атомарная видимость между ресурсами.
        final long txCommitSerial = tx instanceof XdStorageTransaction
                ? ((XdStorageTransaction) tx).getCommitSerial() : 0;
        this.commitSerial = txCommitSerial != 0 ? txCommitSerial : XdStorageTransaction.nextCommitSerial();

        // =========================================================================
        // ФИНАЛЬНЫЙ АРХИТЕКТУРНЫЙ ШТРИХ: Свобождаем тип изменения ноды!
        // Мы обязаны сохранить историю в previousChange для старых транзакций,
        // но текущий маркер change переводим в Change.undefined, чтобы транзакции
        // из будущего могли повторно использовать этот ID для вставок (insert)!
        // =========================================================================
        this.previousChange = this.change;
        this.change = XdStorageCacheChange.undefined;
    }

    public void rollback() {
        this.object = this.prevObject;
        this.prevObject = null;
        this.newObject = null;
        this.state = this.previousState;
        this.previousState = State.undefined;
        this.change = previousChange;
        this.previousChange = XdStorageCacheChange.undefined;

        firstPhaseCommit = false;
        secondPhaseCommit = false;

        this.timestamp = this.prevTimestamp;
        this.prevTimestamp = Long.MIN_VALUE;
    }

    /**
     * R-40: откат INSERT-записи инфраструктурного (B+Tree) объекта. Мутации дерева
     * выполняются НА МЕСТЕ на живом каноническом инстансе: откаченный сплит физически
     * остается в структуре (parent.children), поэтому удалять запись из кэша нельзя —
     * каждый спуск через узел-сироту падал "does not exists". Запись возвращается в
     * состояние чистого чтения текущего живого инстанса: узел разрешим и обновляем
     * любой транзакцией (state=undefined), его содержание совпадает с той версией
     * структуры, на которую и ссылается память дерева.
     */
    public void rollbackInsertKeepServing() {
        final Object liveInstance = this.newObject;
        rollback();
        this.object = liveInstance;
        this.state = State.undefined;
        this.change = XdStorageCacheChange.read;
        this.transaction = null;
        this.timestamp = Long.MIN_VALUE;
        this.prevTimestamp = Long.MIN_VALUE;
        log.debug("infra insert rollback: record {} stays serving the live instance (R-40)", id);
    }

    /**
     * R-45: если запись захвачена чужой транзакцией, УЖЕ ВСТУПИВШЕЙ в фазу коммита, и
     * снапшот читателя включает ее единый commit-serial — возвращает этот serial
     * (новое состояние записи обязано быть видно читателю атомарно). Иначе 0.
     */
    public long committingSerialVisibleTo(final XdStorageTransaction reader) {
        final XdStorageTransaction owner = this.transaction;
        if (owner == null || owner == reader || !firstPhaseCommit) {
            return 0;
        }
        final long ownerSerial = owner instanceof XdStorageTransaction
                ? ((XdStorageTransaction) owner).getCommitSerial() : 0;
        if (ownerSerial == 0) {
            return 0;
        }
        final Long readerSnapshot = reader.getSnapshotSerial();
        return readerSnapshot != null && readerSnapshot >= ownerSerial ? ownerSerial : 0;
    }

    private Object getObjectForTransaction(XdStorageTransaction transaction) {
        Object object;
        if (isChangedByTransaction(transaction)) {
            // СОБСТВЕННЫЕ незакоммиченные мутации: newObject (null после своего delete = скрыто)
            object = this.newObject;
        } else if (servesCommittedVersion(transaction)) {
            // читатель зафиксировал снапшот ДО фактического коммита текущей версии — видит предыдущую
            object = this.prevObject;
        } else if (canBeChangedByTransaction(transaction)) {
            object = this.object;
        } else {
            object = this.prevObject;
        }
        return object;
    }

    /**
     * SNAPSHOT-гейт видимости версии: читатель, начавший транзакцию раньше фактического
     * коммита версии (порядок по счетчику коммитов, а не по часам), видит предыдущую версию.
     * Для мок-транзакций (без снапшот-номера) и незакоммиченных (locked) записей границей
     * остается timestart владельца. READ_COMMITTED: гейт выключен — всегда последнее
     * закоммиченное состояние.
     */
    private boolean servesCommittedVersion(XdStorageTransaction transaction) {
        if (cache.getIsolationLevel() == XdStorageIsolationLevel.READ_COMMITTED) {
            return false;
        }
        if (this.state == State.committed) {
            // 0 = «нет снапшот-номера» (мок-транзакции Mockito) — откат к timestart-сравнению
            final Long readerSerial = transaction.getSnapshotSerial();
            return readerSerial != null && readerSerial > 0
                    ? readerSerial < this.commitSerial
                    : transaction.getTimestart() < this.timestamp;
        }
        return transaction.getTimestart() < this.timestamp;
    }

    /**
     * SNAPSHOT: читатель с timestart < версии видит предыдущую закоммиченную версию.
     * READ_COMMITTED: читатель всегда видит последнее закоммиченное состояние (object).
     */
    private boolean servesSnapshotVersion(final XdStorageTransaction transaction) {
        return cache.getIsolationLevel() != XdStorageIsolationLevel.READ_COMMITTED
                && transaction.getTimestart() < this.timestamp;
    }
}
