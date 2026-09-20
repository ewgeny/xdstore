package org.flib.xdstorage.transaction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Изолированный движок протокола двухфазной фиксации (Two-Phase Commit Engine).
 * Управляет безопасным от дедлоков захватом блокировок ресурсов и координацией фаз коммита/отката.
 */
public final class XdStorageTransaction2PCEngine {

    private static final Logger log = LogManager.getLogger(XdStorageTransaction2PCEngine.class);

    private XdStorageTransaction2PCEngine() {
        // Утилитный класс-оркестратор, запрет инстанцирования
    }

    /**
     * Оркестрирует фазы PREPARE и COMMIT в рамках двухфазного коммита (2PC).
     */
    public static void executeCommit(
            final XdStorageTransaction tx,
            final Queue<XdStorageTransaction.ComparableResourceObject> forCommit,
            final List<IXdStorageResourceObject> lockedResources,
            final Map<Object, IXdStorageResourceObject> resources,
            final XdStorageCommitTransactionStateHolder state,
            final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) throws XdStorageException {

        state.setState(XdStorageCommitTransactionState.PREPARING);

        // 1. Детерминированный захват блокировок (Защита от дедлоков)
        final Iterator<XdStorageTransaction.ComparableResourceObject> it = forCommit.iterator();
        while (it.hasNext()) {
            final IXdStorageResourceObject res = it.next().resource;
            if (!res.hasChanges(tx)) {
                it.remove();
                continue;
            }
            res.lockForCommit(tx);
            lockedResources.add(res);
        }

        final AtomicBoolean hasError = new AtomicBoolean(false);
        final XdStorageRuntimeException[] exceptions = new XdStorageRuntimeException[]{null};

        // 2. Фаза 1: Prepare (Линейная прогонка)
        XdStorageTransaction.ComparableResourceObject resObject;
        while ((resObject = forCommit.poll()) != null) {
            if (hasError.get()) {
                break;
            }
            try {
                final IXdStorageResourceObject res = resObject.resource;
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(res);
                firstPhaseCommittedResources.put(res.getResourceId(), record);
                res.performFirstPhaseCommit(tx, record);
            } catch (final Throwable e) {
                hasError.set(true);
                exceptions[0] = new XdStorageRuntimeException("Сбой транзакции во время фазы Prepare ресурсов", e);
                break;
            }
        }

        if (exceptions[0] != null) {
            throw exceptions[0];
        }

        state.setState(XdStorageCommitTransactionState.PREPARED);

        // 3. Фаза 2: Commit (Безопасная параллельная запись на диск)
        resources.values().parallelStream().forEach(resource -> {
            if (hasError.get()) {
                return;
            }
            try {
                final XdStorageTransactionResourceChanges record = new XdStorageTransactionResourceChanges(resource);
                resource.performSecondPhaseCommit(tx, record);
            } catch (final Throwable e) {
                if (!hasError.getAndSet(true)) {
                    exceptions[0] = new XdStorageRuntimeException("Критический сбой СУБД во время фазы Commit ресурсов", e);
                }
            }
        });

        if (exceptions[0] != null) {
            throw exceptions[0];
        }

        state.setState(XdStorageCommitTransactionState.FINISHED);

        // 4. Безопасное параллельное снятие блокировок
        lockedResources.parallelStream().forEach(res -> res.unlockAfterCommit(tx));
    }

    /**
     * Выполняет параллельный аварийный откат ресурсов в случае падения первой фазы 2PC.
     */
    public static boolean executeFailedCommitRollback(
            final XdStorageTransaction tx,
            final Map<Object, IXdStorageResourceObject> resources,
            final List<IXdStorageResourceObject> lockedResources,
            final Map<Object, XdStorageTransactionResourceChanges> firstPhaseCommittedResources) {

        final List<XdStorageRuntimeException> exceptions = Collections.synchronizedList(new ArrayList<>());

        firstPhaseCommittedResources.entrySet().parallelStream().forEach(entry -> {
            try {
                resources.get(entry.getKey()).rollbackPerformingFirstPhaseCommit(tx, entry.getValue().getChangesObjects());
            } catch (final Throwable e) {
                exceptions.add(new XdStorageRuntimeException("Ошибка отката ресурсов на фазе 1", e));
            }
        });

        lockedResources.parallelStream().forEach(res -> res.unlockAfterCommit(tx));

        if (!exceptions.isEmpty()) {
            exceptions.forEach(e -> log.error("Критический сбой компенсационного отката 2PC", e));
        }

        return exceptions.isEmpty();
    }
}
