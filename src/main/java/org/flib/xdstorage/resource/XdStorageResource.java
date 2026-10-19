package org.flib.xdstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.serialization.IXdStorageIOFactory;
import org.flib.xdstorage.serialization.IXdStorageObjectsReader;
import org.flib.xdstorage.serialization.IXdStorageObjectsWriter;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageAtomicFile;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageResource extends XdStorageAbstractResource {

    private static final Logger log = LogManager.getLogger(XdStorageResource.class);

    private final IXdStorageObjectsReader reader;

    private final IXdStorageObjectsWriter writer;

    /** W-1: append-only commit journal; null = journaling off (full-file rewrite per commit, прежнее поведение). */
    private final java.nio.file.Path journalFile;

    private final org.flib.xdstorage.serialization.yaml.XdStorageYamlIOFactory yamlJournalFactory;

    private Lock lock = new ReentrantLock();

    private AtomicBoolean prepared = new AtomicBoolean(false);

    public XdStorageResource(final XdStorageServicesLocator services,
                             final XdStorageAbstractResourcesManager manager,
                             final Object resourceId,
                             final XdStorageClassInfo clInfo,
                             final IXdStorageIOFactory factory) {
        this(services, manager, resourceId, clInfo, null, false, factory);
    }

    public XdStorageResource(final XdStorageServicesLocator services,
                             final XdStorageAbstractResourcesManager manager,
                             final Object resourceId, final XdStorageClassInfo clInfo,
                             final IXdStorageIdGenerator idGenerator,
                             final boolean isReferences,
                             final IXdStorageIOFactory factory) {
        super(services, manager, resourceId, clInfo, idGenerator, isReferences);
        this.reader = factory.newInstanceReader();
        this.writer = factory.newInstanceWriter();
        this.journalWrites = services.getStorageConfig() != null && services.getStorageConfig().isJournalWrites();
        this.journalFile = journalWrites
                ? org.flib.xdstorage.serialization.yaml.XdStorageYamlCommitJournal.journalFileFor(getFileName())
                : null;
        this.yamlJournalFactory = journalWrites && factory instanceof org.flib.xdstorage.serialization.yaml.XdStorageYamlIOFactory
                ? (org.flib.xdstorage.serialization.yaml.XdStorageYamlIOFactory) factory
                : null;
    }

    private final boolean journalWrites;

    private Map<String, Long> blockingTime = new HashMap<>();

    private AtomicBoolean locked = new AtomicBoolean(false);

    @Override
    public void lockForCommit(final XdStorageTransaction transaction) {
        super.lockForCommit(transaction);

        // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Полностью ликвидируем опасный synchronized(locked) и locked.wait()!
        // Вместо кустарного монитора используем промышленный ReentrantLock 'lock' с поддержкой
        // tryLock() по таймауту транзакции. Если ресурс занят другим коммитом, поток чисто
        // дождется его или выбросит контролируемый таймаут, не утягивая ядро в циклический Deadlock!
        final long timeout = transaction != null ? transaction.getTimeout() : 3000L;
        try {
            boolean acquired = lock.tryLock(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new XdStorageRuntimeException("resource " + getFileName() + " cannot be locked for commit by transaction "
                        + transaction.getTransactionId() + " (Timeout " + timeout + " ms expired)");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XdStorageRuntimeException("waiting for lock resource " + getFileName() + " has been interrupted", e);
        }
    }

    @Override
    public void unlockAfterCommit(final XdStorageTransaction transaction) {
        // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Освобождаем ReentrantLock вместо synchronized-уведомлений
        if (((ReentrantLock) lock).isHeldByCurrentThread()) {
            lock.unlock();
        }

        super.unlockAfterCommit(transaction);
    }

    public String getFileName() {
        return resourceId.toString();
    }

    private Collection<Object> readAsObjects() throws XdStorageException {
        Collection<Object> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new java.io.BufferedReader(new FileReader(file), 1 << 16);
                try {
                    if (isReferences) {
                        objects = reader.readReferences(xmlReader, idField);
                    } else {
                        objects = reader.read(xmlReader);
                    }
                } finally {
                    xmlReader.close();
                }
            }
        } catch (final Throwable e) {
            throw new XdStorageException("invalid state of database: error by reading resource " + resourceId, e);
        }
        return objects;
    }

    private void writeObjects(final Collection<Object> objects) {
        if (objects.size() == 0) {
            // пустой ресурс = файла нет (прежняя семантика)
            final File file = new File(getFileName());
            if (file.exists())
                file.delete();
            return;
        }


        // R-43: guard-ы берутся ДО атомарной записи и отпускаются гарантированно в finally
        final List<Object> guarded = new ArrayList<>();
        final long writeStartNanos = System.nanoTime();
        try {
            for (final Object object : objects) {
                if (object instanceof org.flib.xdstorage.serialization.IXdStorageSerializationGuard) {
                    ((org.flib.xdstorage.serialization.IXdStorageSerializationGuard) object).beginConsistentSerialization();
                    guarded.add(object);
                }
            }
            // crash-safe: пишем во временный файл-соседа и атомарно подменяем ресурс,
            // чтобы сбой посреди коммита не мог обрезать существующий файл ресурса
            XdStorageAtomicFile.writeReplacing(getFileName(), services.getStorageConfig().isDurableWrites(), out -> {
                if (isReferences) {
                    writer.writeReferences(out, idField, objects);
                } else {
                    // R-43: живые инстансы (канонический реестр B+Tree) сериализуются под
                    // guard-ом самого объекта — параллельный сплит/джойн больше не рвет
                    // LinkedList под итератором писателя (CME) и не оставляет рваный XML.
                    writer.writeObjects(out, objects);
                }
            });
            services.getMetrics().increment(org.flib.xdstorage.metrics.XdStorageMetrics.RESOURCE_WRITE_OUTS);
            services.getMetrics().add(org.flib.xdstorage.metrics.XdStorageMetrics.RESOURCE_WRITE_OUT_NANOS,
                    System.nanoTime() - writeStartNanos);
        } catch (final Throwable e) {
            throw new XdStorageRuntimeException("invalid state of database: error by writing resource " + resourceId, e);
        } finally {
            for (final Object object : guarded) {
                ((org.flib.xdstorage.serialization.IXdStorageSerializationGuard) object).endConsistentSerialization();
            }
        }
    }

    public Collection<XdStorageIdentifiableObject> readAsData(final XdStorageTransaction transaction) throws XdStorageException {
        Collection<XdStorageIdentifiableObject> objects = null;
        try {
            final File file = new File(getFileName());
            if (file.exists()) {
                Reader xmlReader = new java.io.BufferedReader(new FileReader(file), 1 << 16);
                try {
                    objects = reader.readData(xmlReader, idField);
                } finally {
                    xmlReader.close();
                }
            }
        } catch (final Throwable e) {
            throw new XdStorageException("invalid state of database: error by reading resource " + resourceId, e);
        }
        return objects;
    }

    public void prepare(final XdStorageTransaction transaction) throws XdStorageException {
        if (prepared.get()) {
            return;
        }

        lock.lock();
        try {
            if (prepared.get()) {
                return;
            }

            Collection<Object> objects = readAsObjects();
            log.info("resource {} prepared from disk: {} objects", getFileName(), objects != null ? objects.size() : 0);
            if (objects != null)
                cache.fillCache(objects);
            if (journalWrites) {
                replayCommitJournal();
            }
            postPrepare(transaction);
            manager.updateCounter(getObjectsClass(), this);

            prepared.set(true);
        } finally {
            lock.unlock();
        }
    }

    /** W-1: восстановление закоммиченного состояния из журнала поверх базы (идемпотентно, last writer wins). */
    private void replayCommitJournal() throws XdStorageException {
        try {
            final org.flib.xdstorage.serialization.yaml.XdStorageYamlCommitJournal.Replay replay =
                    org.flib.xdstorage.serialization.yaml.XdStorageYamlCommitJournal.readForReplay(
                            journalFile, idField.field.getType(), yamlJournalFactory);
            for (final Object upsert : replay.upserts) {
                cache.applyCommittedJournalUpsert(upsert);
            }
            for (final Object id : replay.deletes) {
                cache.applyCommittedJournalDelete(id);
            }
            if (!replay.upserts.isEmpty() || !replay.deletes.isEmpty()) {
                log.info("resource {} replayed commit journal: {} upsert(s), {} delete(s), {} discarded tail record(s)",
                        getFileName(), replay.upserts.size(), replay.deletes.size(), replay.discardedTailRecords);
            }
        } catch (final Throwable e) {
            throw new XdStorageException("invalid state of database: commit journal of resource " + resourceId + " cannot be replayed", e);
        }
    }

    public void performFirstPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageException {
        if (cache.hasChanges(transaction)) {
            if (journalWrites) {
                // W-1: журнал — точка записи фазы 1: O(delta) аппенд вместо полной перезаписи файла.
                // Изменения берутся ПОСЛЕ prepareCommit — коллектор (type, id, old, new) заполнен им.
                cache.prepareCommit(transaction, record);
                try {
                    org.flib.xdstorage.serialization.yaml.XdStorageYamlCommitJournal.appendRedo(journalFile,
                            transaction.getCommitSerial(), record.getChangesObjects(),
                            services, yamlJournalFactory, services.getStorageConfig().isDurableWrites());
                } catch (final Throwable e) {
                    throw new XdStorageRuntimeException("invalid state of database: error by appending commit journal of resource " + resourceId, e);
                }
                checkpointIfJournalTooBig(transaction);
            } else {
                final Collection<Object> objects = cache.readUnsafe(transaction);
                cache.prepareCommit(transaction, record);
                writeObjects(objects);
            }
        }
    }

    /** W-1: порог размера журнала — чекпоинт (полная перезапись базы существующим writer-ом + сброс журнала). */
    private void checkpointIfJournalTooBig(final XdStorageTransaction transaction) throws XdStorageException {
        final long threshold = services.getStorageConfig().getJournalCheckpointBytes();
        if (org.flib.xdstorage.serialization.yaml.XdStorageYamlCommitJournal.sizeOf(journalFile) <= threshold) {
            return;
        }
        log.info("resource {} commit journal exceeded {} bytes — checkpointing (full base rewrite + journal reset)",
                getFileName(), threshold);
        writeObjects(cache.readUnsafe(transaction));
        try {
            org.flib.xdstorage.serialization.yaml.XdStorageYamlCommitJournal.reset(journalFile);
        } catch (final Throwable e) {
            // безвредно: база уже содержит все закоммиченное; повтор реплея поверх нее идемпотентен
            log.warn("commit journal {} could not be reset after checkpoint (safe: replay is idempotent): {}",
                    journalFile, e.getMessage());
        }
    }

    public void performSecondPhaseCommit(final XdStorageTransaction transaction, final XdStorageTransactionResourceChanges record) throws XdStorageException {
        cache.performCommit(transaction, record);
        cache.commit(transaction);
        postCommit(transaction);
    }

    public void rollbackPerformingFirstPhaseCommit(final XdStorageTransaction transaction, final Collection<XdStorageObjectChange> changes) throws XdStorageException {
        cache.rollbackFailedCommit(transaction, changes);
        if (journalWrites) {
            // W-1: компенсация — восстановленное (до-транзакционное) состояние каждого измененного id.
            // Писать в журнал обязательно: без этого обрыв после аппенда redo вернул бы отклоненную транзакцию при реплее.
            try {
                org.flib.xdstorage.serialization.yaml.XdStorageYamlCommitJournal.appendCompensation(journalFile,
                        changes, services, yamlJournalFactory, services.getStorageConfig().isDurableWrites());
            } catch (final Throwable e) {
                throw new XdStorageRuntimeException("invalid state of database: error by appending commit journal compensation of resource " + resourceId, e);
            }
        } else {
            writeObjects(cache.readUnsafe(transaction));
        }
        postRollback(transaction);
    }

    @Override
    public void rollback(final XdStorageTransaction transaction) throws XdStorageException {
        // Гарантируем fail-safe отпуск ReentrantLock замка при откате изменений страницы
        if (((ReentrantLock) lock).isHeldByCurrentThread()) {
            lock.unlock();
        }
        cache.rollback(transaction);
        postRollback(transaction);
    }
}
