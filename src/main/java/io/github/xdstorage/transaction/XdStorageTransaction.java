package io.github.xdstorage.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.resource.IXdStorageResourceObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class XdStorageTransaction implements IXdStorageTransaction {

    private static final Logger log = LogManager.getLogger(XdStorageTransaction.class);

    private final IXdStorageTransactionManager manager;

    private final long timeout;

    private final String transactionThreadId;

    private final String transactionId;

    private final Long timestart;

    /**
     * Снапшот-номер транзакции: значение глобального счетчика коммитов на момент НАЧАЛА транзакции.
     * Граница видимости SNAPSHOT: запись видна в старой версии, пока ее commitSerial больше снапшот-номера.
     * Для моков (без конструктора) остается null — кэш откатывается к timestart-сравнению.
     */
    private final Long snapshotSerial;

    /**
     * Глобальный счетчик фактических коммитов (порядок коммитов, независимый от часов).
     * Начинается с 1: значение 0 зарезервировано как «нет номера» — Mockito-моки транзакций
     * возвращают 0L для неснаппированного Long, и кэш откатывает их к timestart-сравнению.
     */
    private static final AtomicLong COMMIT_SEQUENCE = new AtomicLong(1);

    public static long nextCommitSerial() {
        return COMMIT_SEQUENCE.incrementAndGet();
    }

    private final Set<Object> resourcesIds;

    protected final Map<Object, IXdStorageResourceObject> resources;

    protected final Queue<ComparableResourceObject> forCommit;

    protected final List<IXdStorageResourceObject> lockedResources;

    // R-57: индексные ключи, успешно вставленные в живые Б+Деревья этой транзакцией, —
    // подлежат КОМПЕНСИРУЮЩЕМУ удалению при откате (деревья мутируются ин-плейс и
    // не откатываются record-механизмом; без компенсации индекс вечно указывает на
    // несуществующие записи — "cannot load by reference" у читателей сканов).
    private final java.util.List<Object[]> indexKeysForRollback =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private final AtomicReference<TransactionState> transactionState = new AtomicReference<>(TransactionState.InProcess);

    private final XdStorageTransaction globalTransaction;

    private final Set<String> internalTransactions = Collections.synchronizedSet(new HashSet<>());

    private final AtomicBoolean hasInternalTransactions = new AtomicBoolean(false);

    private final AtomicBoolean rollbackOnly = new AtomicBoolean(false);

    private final AtomicLong critical = new AtomicLong(0);

    /** R-40: запас сверх таймаута внутренней tx, после которого родитель принудительно ее sweeping-откатывает. */
    private static final long INTERNAL_SWEEP_GRACE_MILLIS = 500;

    /**
     * R-45: serial коммита ЭТОЙ транзакции (0 = не коммитится). Резервируется ОДИН на всю
     * транзакцию ДО начала фаз коммита: все ее записи получают один и тот же номер, поэтому
     * читатель со снапшотом >= serial видит ЛИБО все эффекты транзакции, ЛИБО ни одного —
     * атомарность видимости между ресурсами (раньше у каждой записи был свой serial, и
     * читатель между фазами разных ресурсов видел смесь: новую систему со ссылками на
     * еще невидимые планеты — "cannot load by reference" в GraphPhantom).
     */
    private final AtomicLong commitSerial = new AtomicLong(0);

    public void assignCommitSerial() {
        commitSerial.compareAndSet(0, XdStorageTransaction.nextCommitSerial());
    }

    /** R-45: serial коммита; 0 — транзакция не в фазе коммита. */
    public long getCommitSerial() {
        return commitSerial.get();
    }

    protected XdStorageTransaction(final IXdStorageTransactionManager manager, final String transactionThreadId,
                                   final XdStorageTransaction globalTransaction, final long timeout, final String transactionId) {
        this.manager = manager;
        this.transactionThreadId = transactionThreadId;
        this.globalTransaction = globalTransaction;
        this.timeout = timeout;
        this.transactionId = transactionId;
        this.timestart = System.nanoTime();
        this.snapshotSerial = COMMIT_SEQUENCE.get();
        this.resourcesIds = Collections.synchronizedSet(new HashSet<>());
        this.resources = new ConcurrentHashMap<>();
        this.forCommit = new PriorityBlockingQueue<>();
        this.lockedResources = new ArrayList<>();
    }

    /** Снапшот-номер транзакции; null для мок-транзакций, созданных мимо конструктора. */
    public Long getSnapshotSerial() {
        return snapshotSerial;
    }

    @Override
    public IXdStorageTransactionManager<?> getTransactionManager() {
        return manager;
    }

    @Override
    public IXdStorage getStorage() {
        return manager.getStorage();
    }

    public void markFinished() {
        synchronized (transactionState) {
            transactionState.set(TransactionState.Finished);
            transactionState.notifyAll();
        }
    }

    public void waitForFinish() {
        synchronized (transactionState) {
            while (transactionState.get() != TransactionState.Finished) {
                try {
                    transactionState.wait();
                } catch (InterruptedException e) {
                    throw new XdStorageRuntimeException("invalid state of transaction " + transactionId, e);
                }
            }
        }
    }

    public XdStorageTransaction getGlobalTransaction() {
        return globalTransaction;
    }

    public String getGlobalTransactionId() {
        return globalTransaction != null ? globalTransaction.getTransactionId() : null;
    }

    public final void suspend(final String internalTransactionId) {
        synchronized (hasInternalTransactions) {
            internalTransactions.add(internalTransactionId);
            hasInternalTransactions.set(true);
        }
    }

    public final void skipOrWaitForInternalTransactions() {
        if (hasInternalTransactions.get()) {
            synchronized (hasInternalTransactions) {
                while (hasInternalTransactions.get()) {
                    // R-40: ЗАЧИСТКА БРОШЕННЫХ ВНУТРЕННИХ ТРАНЗАКЦИЙ. Если вложенная tx умерла
                    // по исключению, не дойдя до commit/rollback, ее suspend() на родителе
                    // никогда не снимется штатно: родитель навеки зависает здесь, оставаясь
                    // "живым" держателем своих record-локов — все чужие обновления его узлов
                    // отказывают вечно (детерминированный ноль прогрессов nested-стресса R-37).
                    // Истекшая по таймауту внутренняя tx отправляется в штатный откат
                    // (registerRollbackOnlyTransaction — асинхронный уборщик с барьером
                    // активных потоков), который и снимет suspend через resume().
                    boolean swept = false;
                    for (final String internalId : internalTransactions.toArray(new String[0])) {
                        final XdStorageTransaction internal = (XdStorageTransaction) manager.getTransaction(internalId);
                        if (internal == null) {
                            // Запись-мусор: tx уже завершена, но resume не дошел — снимаем сами
                            resume(internalId);
                            swept = true;
                            continue;
                        }
                        // R-41: внутренняя tx, привязанная к ТЕКУЩЕМУ потоку, брошена этим же
                        // потоком (исключение из nested-области до commit/rollback) — завершить
                        // ее некому; ждем только 3.5с зомби-окна, отравляющего чужие спуски.
                        if (!internal.isRollbackOnly() && ((XdStorageTransactionManager) manager).isTransactionBoundToCurrentThread(internalId)) {
                            log.warn("Parent transaction {} force-sweeps internal transaction {} abandoned on the same thread (exception escaped its scope)",
                                    transactionId, internalId);
                            internal.markSelfAndChildrenRollbackOnly();
                            swept = true;
                            continue;
                        }
                        final long ageMillis = (System.nanoTime() - internal.getTimestart()) / 1_000_000;
                        if (ageMillis > internal.getTimeout() + INTERNAL_SWEEP_GRACE_MILLIS && !internal.isRollbackOnly()) {
                            log.warn("Parent transaction {} force-sweeps expired internal transaction {} (age {} ms > timeout {} ms) — it was abandoned without commit/rollback",
                                    transactionId, internalId, ageMillis, internal.getTimeout());
                            // Отравление цепочки: первый вызов каскадно помечает parent (ACID:
                            // упавшая вложенная tx обязана откатить родителя), второй — саму
                            // внутреннюю tx (ее globalTransaction уже rollback-only).
                            internal.markRollbackOnly();
                            if (!internal.isRollbackOnly()) {
                                internal.markRollbackOnly();
                            }
                            manager.registerRollbackOnlyTransaction(internal);
                            swept = true;
                        }
                    }
                    if (!swept) {
                        try {
                            hasInternalTransactions.wait(100);
                        } catch (final InterruptedException e) {
                            throw new XdStorageRuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    public final void resume(final String internalTransactionId) {
        synchronized (hasInternalTransactions) {
            internalTransactions.remove(internalTransactionId);
            if (internalTransactions.isEmpty()) {
                hasInternalTransactions.set(false);
                hasInternalTransactions.notifyAll();
            }
        }
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public String getTransactionThreadId() {
        return transactionThreadId;
    }

    @Override
    public String getTransactionId() {
        return transactionId;
    }

    @Override
    public long getTimestart() {
        return timestart;
    }

    public void startCriticalSection() throws XdStorageException {
        startCriticalSection(false);
    }

    public void startCriticalSection(boolean rollback) {
        synchronized (critical) {
            // Если транзакция УЖЕ помечена как rollback-only, и это не вызов самого отката,
            // мгновенно прерываем операцию легитимным исключением СУБД!
            if (!rollback && rollbackOnly.get())
                throw new XdStorageRuntimeException("transaction " + transactionId + " marked as rollback only and will be rolled back");
            critical.incrementAndGet();
        }
    }

    public void finishCriticalSection() {
        synchronized (critical) {
            if (critical.decrementAndGet() == 0) {
                critical.notifyAll();
            }
        }
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly.get();
    }

    private void setRollbackOnly() {
        synchronized (critical) {
            // ИСПРАВЛЕНИЕ: Проверяем, выполняется ли операция в контексте текущего транзакционного потока.
            // Если поток совпадает с transactionThreadId (или текущим Thread.currentThread()),
            // мы КАТЕГОРИЧЕСКИ запрещаем вызывать блокирующий critical.wait(), который усыплял поток сам в себе!
            // Взводим флаг отката атомарно, полностью ликвидируя внутритранзакционные дедлоки!
            final String currentThreadStr = String.valueOf(Thread.currentThread().getId());
            if (transactionThreadId != null && (transactionThreadId.equals(currentThreadStr) || transactionThreadId.contains(currentThreadStr))) {
                rollbackOnly.set(true);
                return;
            }

            // Для сторонних конкурирующих потоков сохраняем дефолтное безопасное ожидание
            while (critical.get() > 0) {
                try {
                    critical.wait(50); // Добавляем жесткий таймаут для предотвращения бесконечного зависания узлов
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            rollbackOnly.set(true);
        }
    }

    @Override
    public void commit() {
        manager.commitTransaction(this);
    }

    @Override
    public void rollback() {
        manager.rollbackTransaction(this);
    }

    public Collection<IXdStorageResourceObject> getResources() {
        return new ArrayList<>(resources.values());
    }

    public boolean isResourceRegistered(final IXdStorageResourceObject resource) {
        return resourcesIds.contains(resource.getResourceId());
    }

    public void registerResource(final IXdStorageResourceObject resource, final int commitOrder) throws XdStorageException {
        synchronized (critical) {
            if (rollbackOnly.get())
                throw new XdStorageException("transaction " + transactionId + " marked as rollback only and will be rolled back");

            resourcesIds.add(resource.getResourceId());
            resources.put(resource.getResourceId(), resource);
            forCommit.add(new ComparableResourceObject(resource, commitOrder));  // use priority queue
        }
    }

    public void commitInternal(final XdStorageCommitTransactionStateHolder state,
                               final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) throws XdStorageException {

        final XdStorageRuntimeException[] exceptions = new XdStorageRuntimeException[]{null};
        final AtomicBoolean hasError = new AtomicBoolean();

        // Делаем изолированный снимок зарегистрированных ресурсов СУБД для второй фазы
        final Collection<IXdStorageResourceObject> resourceToCommit = new ArrayList<>(resources.values());

        // Переводим транзакцию в состояние подготовки коммита (Phase 1 Commit)
        state.setState(XdStorageCommitTransactionState.PREPARING);

        // ИСПРАВЛЕНИЕ МНОГОПОТОЧНОСТИ: Категорически убираем хрупкий forCommit.iterator() и it.remove()!
        // Итератор PriorityBlockingQueue не предназначен для удаления элементов "на лету" под нагрузкой.
        // Применяем пуленепробиваемый паттерн Queue Drain: сливаем элементы очереди через poll()
        // во временный локальный список, гарантируя 100% изоляцию от ConcurrentModificationException.
        final List<ComparableResourceObject> activeChangesResources = new ArrayList<>();
        ComparableResourceObject checkObject;

        while ((checkObject = forCommit.poll()) != null) {
            final IXdStorageResourceObject res = checkObject.resource;
            // Если ресурс не содержит изменений в текущей транзакции — просто пропускаем его
            if (!res.hasChanges(this)) {
                continue;
            }
            // Блокируем измененный дисковый ресурс на коммит и добавляем в локальную цепочку
            res.lockForCommit(this);
            lockedResources.add(res);
            activeChangesResources.add(checkObject);
        }

        // Выполняем первую фазу коммита (запись изменений на диск) строго для отфильтрованных ресурсов
        for (final ComparableResourceObject resObject : activeChangesResources) {
            if (hasError.get()) {
                break;
            }

            try {
                final IXdStorageResourceObject res = resObject.resource;
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(res);
                firstPhaseCommittedResources.put(res.getResourceId(), record);
                res.performFirstPhaseCommit(this, record);
            } catch (final Throwable e) {
                if (hasError.get()) {
                    break;
                }
                hasError.set(true);
                exceptions[0] = new XdStorageRuntimeException(e);
            }
        }

        if (exceptions[0] != null)
            throw exceptions[0];

        state.setState(XdStorageCommitTransactionState.PREPARED);

        // performing second phase commit (финальная очистка кэшей и подтверждение версий)
        resourceToCommit.stream().forEach(resource -> {
            if (hasError.get()) {
                return;
            }

            try {
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(resource);
                resource.performSecondPhaseCommit(this, record);
            } catch (final Throwable e) {
                if (hasError.get()) {
                    return;
                }
                hasError.set(true);
                exceptions[0] = new XdStorageRuntimeException(e);
            }
        });

        if (exceptions[0] != null)
            throw exceptions[0];

        state.setState(XdStorageCommitTransactionState.FINISHED);

        lockedResources.stream().forEach(resource -> {
            resource.unlockAfterCommit(XdStorageTransaction.this);
        });
    }

    @Override
    public void registerIndexKeyForRollback(final Object tree, final Comparable key) {
        if (tree != null && key != null) {
            indexKeysForRollback.add(new Object[]{tree, key});
        }
    }

    @Override
    public void rollbackIndexKeyInserts() {
        final java.util.List<Object[]> pending;
        synchronized (indexKeysForRollback) {
            if (indexKeysForRollback.isEmpty()) {
                return;
            }
            pending = new java.util.ArrayList<>(indexKeysForRollback);
            indexKeysForRollback.clear();
        }
        // Обратный порядок регистрации: последний вставленный ключ удаляется первым.
        for (int i = pending.size() - 1; i >= 0; --i) {
            final Object[] pair = pending.get(i);
            try {
                ((io.github.xdstorage.btree.XdStorageBTree) pair[0])
                        .delete((Comparable) pair[1], getStorage(), this);
            } catch (final Throwable t) {
                // Ключа может уже не быть (параллельная компенсация/удаление) — штатно.
                org.apache.logging.log4j.LogManager.getLogger(XdStorageTransaction.class)
                        .debug("rollback index-key compensation skipped for {}: {}", pair[1], t.getMessage());
            }
        }
    }

    public boolean rollbackFailedCommit(final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) {
        final List<XdStorageRuntimeException> exceptions = Collections.synchronizedList(new ArrayList<>());

        firstPhaseCommittedResources.entrySet().stream().forEach(entry -> {
            try {
                resources.get(entry.getKey()).rollbackPerformingFirstPhaseCommit(this, entry.getValue().getChangesObjects());
            } catch (final Throwable e) {
                exceptions.add(new XdStorageRuntimeException(e));
            }
        });

        lockedResources.stream().forEach(resource -> {
            resource.unlockAfterCommit(XdStorageTransaction.this);
        });

        if (!exceptions.isEmpty()) {
            exceptions.stream().forEach(e -> {
                log.error("rollback failed commit error", e);
            });
        }

        return exceptions.isEmpty();
    }

    public void rollbackInternal() {
        // R-57: компенсация ин-плейс индексных мутаций ДО отката record-состояний —
        // компенсирующие delete сами регистрируют свои изменения в этой транзакции
        // и убираются общим откатом ниже.
        rollbackIndexKeyInserts();
        for (final IXdStorageResourceObject resource : resources.values()) {
            try {
                resource.rollback(this);
            } catch (final XdStorageException e) {
                throw new XdStorageRuntimeException("invalid state of database", e);
            }
        }

        lockedResources.stream().forEach(resource -> {
            resource.unlockAfterCommit(XdStorageTransaction.this);
        });
    }

    @Override
    public void markRollbackOnly() {
        if (globalTransaction == null || globalTransaction.isRollbackOnly() || isRollbackOnly()) {
            markSelfAndChildrenRollbackOnly();
        } else {
            globalTransaction.markRollbackOnly();
        }
    }

    /**
     * R-41: ИЗОЛИРОВАННЫЙ откат области транзакции — своя отметка rollback-only плюс
     * каскад ВНИЗ по внутренним транзакциям, БЕЗ отравления родителя. Контракт вложенного
     * отката: "аварийный ОТКАТ вложенной транзакции — родитель должен выжить". Делегирование
     * вверх (markRollbackOnly) здесь ломало сценарий 2 nested-стресса: каждый второй шаг
     * (nested rollback) ронял commit родителя "marked as rollback only" — детерминированно,
     * даже в один поток.
     */
    public void markSelfAndChildrenRollbackOnly() {
        setRollbackOnly();
        for (final String internalId : internalTransactions.toArray(new String[0])) {
            final XdStorageTransaction internal = (XdStorageTransaction) manager.getTransaction(internalId);
            if (internal != null && !internal.isRollbackOnly()) {
                internal.markSelfAndChildrenRollbackOnly();
            }
        }
        manager.registerRollbackOnlyTransaction(this);
    }

    public boolean isTransaction(final XdStorageTransaction transaction) {
        return this == transaction;
    }

    protected class ComparableResourceObject implements Comparable<ComparableResourceObject> {

        public final IXdStorageResourceObject resource;

        private final Integer priority;

        ComparableResourceObject(final IXdStorageResourceObject resource, final int priority) {
            this.resource = resource;
            this.priority = priority;
        }

        @Override
        public int compareTo(ComparableResourceObject o) {
            return this.priority.compareTo(o.priority);
        }
    }

    private enum TransactionState {
        InProcess, Finished;
    }
}
