package io.github.xdstorage;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.index.IXdStorageIndexDaoResource;
import io.github.xdstorage.resource.*;
import io.github.xdstorage.search.IXdStorageSearchManager;
import io.github.xdstorage.search.query.XdStorageSearchQuery;
import io.github.xdstorage.serialization.FilesFormat;
import io.github.xdstorage.services.XdStorageServicesLocator;
import io.github.xdstorage.structure.update.IXdStorageStructureUpdater;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.transaction.IXdStorageTransactionManager;
import io.github.xdstorage.transaction.XdStorageTransaction;
import io.github.xdstorage.transaction.XdStorageTransactionManager;
import io.github.xdstorage.trigger.IXdStorageTrigger;
import io.github.xdstorage.utils.XdStorageClassInfo;
import io.github.xdstorage.utils.XdStorageFolderLock;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class presents simple working interface. This one provides simple
 * methods for object persistence.
 */
class XdStorage implements IXdFileStorage {

    private static final Logger log = LoggerFactory.getLogger(XdStorage.class);

    private final String name;

    private final XdStorageServicesLocator services;

    private final XdStorageConfig config;

    /** S-4: после shutdown() любой публичный вызов падает громко, а не с RejectedExecutionException где-то в недрах. */
    private volatile boolean closed = false;

    private final Object shutdownLock = new Object();

    private XdStorageFolderLock folderLock;

    private Thread shutdownHook;

    /**
     * This constructor initialize storage for specified folder. All files will be
     * storaged in this folder.
     *
     * @param folder Storage path.
     */
    XdStorage(final String name, final String folder, final int fragmentSize) {
        this(name, folder, fragmentSize, FilesFormat.YAML, XdStorageConfig.defaults());
    }

    XdStorage(final String name, final String folder, final int fragmentSize, FilesFormat format) {
        this(name, folder, fragmentSize, format, XdStorageConfig.defaults());
    }

    XdStorage(final String name, final String folder, final int fragmentSize, final FilesFormat format, final XdStorageConfig config) {
        this.name = name;
        this.config = config == null ? XdStorageConfig.defaults() : config;
        if (this.config.isJournalWrites() && format != FilesFormat.YAML) {
            // W-1: журнал коммитов реализован для YAML-формата; остальное — громкий отказ при открытии
            throw new XdStorageRuntimeException("journalWrites is supported for the YAML format only (got " + format + ")");
        }
        services = new XdStorageServicesLocator();
        services.initFileConfiguration(this, folder, fragmentSize, format, this.config);
        if (this.config.isRegisterShutdownHook()) {
            // S-4: best-effort закрытие при выходе JVM. НЕ заменяет daemonWorkerThreads:
            // хуки запускаются только после завершения последнего не-daemon потока, поэтому
            // не-daemon пул блокирует выход JVM еще до того, как хуки успеют сработать.
            final Thread hook = new Thread(this::shutdownFromHook, "xdstorage-shutdown-hook-" + name);
            hook.setDaemon(true);
            try {
                Runtime.getRuntime().addShutdownHook(hook);
                this.shutdownHook = hook;
            } catch (final IllegalStateException e) {
                log.warn("shutdown hook could not be registered for storage '{}' (JVM already shutting down)", name);
            }
        }
    }

    /** S-3: блокировку каталога выдает провайдер при создании; освобождается в shutdown(). */
    void attachFolderLock(final XdStorageFolderLock lock) {
        this.folderLock = lock;
    }

    /** S-4: громкий отказ после shutdown() вместо сырых RejectedExecutionException в недрах. */
    private void ensureOpen() {
        if (closed) {
            throw new XdStorageRuntimeException("storage '" + name + "' is shut down — open a new instance via XdStorageProvider");
        }
    }

    private void shutdownFromHook() {
        log.info("JVM shutdown hook closing storage '{}'", name);
        shutdown();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public io.github.xdstorage.metrics.XdStorageMetrics getMetrics() {
        ensureOpen();
        return services.getMetrics();
    }

    /**
     * This method registers trigger. Every trigger will be executed in change
     * transaction and must work only with parameter's object.
     *
     * @param trigger This trigger will be registered.
     */
    @Override
    public <T> void registerTrigger(final IXdStorageTrigger<T> trigger) {
        ensureOpen();
        services.getTriggersManager().registerTrigger(trigger);
    }

    /**
     * This method starts new transaction.
     *
     * @param timeout Transaction timeout in milliseconds
     * @return Return started transaction.
     */
    @Override
    public IXdStorageTransaction beginTransaction(final long timeout) {
        ensureOpen();
        return services.getTransactionsManager().beginTransaction(timeout);
    }

    @Override
    public IXdStorageTransaction beginTransaction() {
        ensureOpen();
        return beginTransaction(config.getDefaultTransactionTimeoutMillis());
    }

    @Override
    public IXdStorageTransaction beginTransaction(final IXdStorageTransaction transaction, final long timeout) {
        ensureOpen();
        return services.getTransactionsManager().beginTransaction(transaction, timeout);
    }

    @Override
    public IXdStorageTransaction beginTransaction(final IXdStorageTransaction transaction) {
        ensureOpen();
        return beginTransaction(transaction, 3 * 1000);
    }

    /**
     * This method commit specified transaction.
     *
     * @param transaction Specified transaction
     */
    @Override
    public void commitTransaction(final IXdStorageTransaction transaction) throws XdStorageException {
        ensureOpen();
        if (transaction == null)
            throw new IllegalArgumentException("transaction cannot be null");

        services.getTransactionsManager().commitTransaction(transaction);
    }

    /**
     * This method roll back specified transaction.
     *
     * @param transaction Specified transaction
     */
    @Override
    public void rollbackTransaction(final IXdStorageTransaction transaction) {
        ensureOpen();
        if (transaction == null)
            throw new IllegalArgumentException("transaction cannot be null");

        services.getTransactionsManager().rollbackTransaction(transaction);
    }

    /**
     * This method checks transaction.
     *
     * @return Return true if transaction has been started.
     */
    private boolean checkIsInTransaction() {
        return services.getTransactionsManager().getCurrentTransaction() != null;
    }

    /**
     * This method checks transaction is not finished.
     *
     * @param transaction
     * @return Returns true if transaction was not committed or rolled back.
     */
    private boolean isTransactionAlive(final IXdStorageTransaction transaction) {
        return services.getTransactionsManager().isTransactionAlive(transaction);
    }

    /**
     * @param updater
     */
    @Override
    public void registerStructureUpdater(final IXdStorageStructureUpdater updater) {
        ensureOpen();
        services.getStructureManager().registerUpdater(updater);
    }

    /**
     * @param cl
     * @throws XdStorageException
     */
    @Override
    public List<IXdStorageStructureUpdater> executeStructureUpdate(final Class<?> cl) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        if (!checkHasObjectIdField(cl))
            throw new XdStorageException("the class " + cl + " must have identifier field @XdStorageObjectId");

        return services.getStructureManager().checkAndUpdateStructureIfNeed(cl, (XdStorageTransaction) services.getTransactionsManager().getCurrentTransaction());
    }

    /**
     * This method saves specified object.
     *
     * @param object Specified object
     * @throws XdStorageException This exception will be throw if an error arises in save
     *                            operation.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void save(final Object object) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        save(object, transaction);
    }

    @Override
    public void save(final Object object, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (object == null)
            throw new XdStorageException("object cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            final Class<?> cl = object.getClass();
            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            persistObject(object, tx);

            log.debug("saved {} by tx {}", cl.getSimpleName(), transaction.getTransactionId());

            // AUTOSAVE (docs/tasks/2.1.0/22): cascade @XdStorageAutoSave children in the same tx
            processAutoSave(object, tx);
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method saves collection of objects.
     *
     * @param objects Collection of objects.
     * @throws XdStorageException This exception will be throw if error arises in save
     *                            operation.
     */
    @Override
    public void save(final Collection<?> objects) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        save(objects, transaction);
    }

    @Override
    public void save(final Collection<?> objects, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (objects == null)
            throw new XdStorageException("objects collection cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (objects.isEmpty())
                return;

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
            final IXdStorageSearchManager searchManager = services.getSearchManager();

            final XdStorageException[] exceptions = new XdStorageException[]{null};
            final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);

            objects.forEach(object -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());

                    if (!checkHasObjectIdField(cl)) {
                        throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");
                    }

                    final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

                    final XdStoragePolicy policy = clInfo.getPolicy();
                    if (policy == XdStoragePolicy.StoreAsClassObjects) {
                        final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                        resource.insert(object, tx);

                        if (searchManager.hasIndex(object)) {
                            searchManager.insert(object, tx);
                        }
                    } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                        final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);
                        referencesResource.insertReference(object, tx);

                        final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(object, tx);
                        resource.insert(object, tx);

                        if (searchManager.hasIndex(object)) {
                            searchManager.insert(object, tx);
                        }
                    } else {
                        throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                                + XdStoragePolicy.StoreAsSingleObject);
                    }
                } catch (final XdStorageConnectionException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    conExceptions[0] = e;
                } catch (final XdStorageException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    exceptions[0] = e;
                }
            });

            if (exceptions[0] != null)
                throw exceptions[0];
            if (conExceptions[0] != null)
                throw conExceptions[0];

            log.debug("saved collection of {} ({} objects) by tx {}", objects.iterator().hasNext() ? objects.iterator().next().getClass().getSimpleName() : "?", objects.size(), transaction.getTransactionId());
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This check existing object by specified identifier.
     *
     * @param cl Specified class
     * @param id Specified identifier
     * @return Return true if and only if object contains in the storage
     */
    @Override
    public boolean has(final Class<?> cl, final Object id) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return has(cl, id, transaction);
    }

    @Override
    public boolean has(final Class<?> cl, final Object id, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (id == null)
            throw new XdStorageException("idgeneration cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        if (!isTransactionAlive(transaction))
            throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

        if (!checkHasObjectIdField(cl))
            throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

        final XdStorageTransaction tx = cast(transaction);

        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
            return resource.read(id, tx) != null;
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(clInfo, id, tx);
            return resource.read(id, tx) != null;
        } else {
            throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                    + XdStoragePolicy.StoreAsSingleObject);
        }
    }

    /**
     * This method loads required object by object's reference.
     *
     * @param reference Specified object's reference.
     * @throws XdStorageException This exception will be throw if error arises in load
     *                            operation.
     */
    @Override
    public void load(final Object reference) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        load(reference, transaction);
    }

    @Override
    public void load(final Object reference, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (reference == null)
            throw new XdStorageException("reference cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            final Class<?> cl = XdStorageObjectUtils.getEntityClass(reference.getClass());
            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                resource.readByReference(reference, tx);
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(reference, tx);
                resource.readByReference(reference, tx);
            } else {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method loads object by specified identifier.
     *
     * @param cl Specified class
     * @param id Specified identifier
     * @return Return object if exist, else will be throw exception
     * @throws XdStorageException This exception will be throw if object isn't exist.
     */
    @Override
    public <T> T load(final Class<T> cl, final Object id) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, id, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(final Class<T> cl, final Object id, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (id == null)
            throw new XdStorageException("idgeneration cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            final T result;
            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                result = (T) resource.read(id, tx);
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(clInfo, id, tx);
                result = (T) resource.read(id, tx);
            } else {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }
            return result;
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method loads some objects in specified references collection.
     *
     * @param references Collection of objects' references.
     * @return Return true if all objects from collection was been loaded
     * successful, else return false.
     * @throws XdStorageException This exception will throw if error arises in loading process.
     */
    @Override
    public void load(final Collection<?> references) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        load(references, transaction);
    }

    @Override
    public void load(final Collection<?> references, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (references == null)
            throw new XdStorageException("references collection cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (references.isEmpty())
                return;

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

            final XdStorageException[] exceptions = new XdStorageException[]{null};
            final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);

            references.forEach(reference -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    final Class<?> cl = XdStorageObjectUtils.getEntityClass(reference.getClass());

                    if (!checkHasObjectIdField(cl)) {
                        throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");
                    }

                    final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

                    final XdStoragePolicy policy = clInfo.getPolicy();
                    if (policy == XdStoragePolicy.StoreAsClassObjects) {
                        final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                        resource.readByReference(reference, tx);
                    } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                        final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(reference, tx);
                        resource.readByReference(reference, tx);
                    } else {
                        throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                                + XdStoragePolicy.StoreAsSingleObject);
                    }
                } catch (final XdStorageConnectionException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    conExceptions[0] = e;
                } catch (final io.github.xdstorage.exceptions.XdStorageReferenceInvisibleException e) {
                    // R-58: the element is invisible in the reader snapshot (committed after its start /
                    // transiently rolled back) - normal MVCC answer, not an error: the element is not
                    // part of the visible graph version. The reference stays unfilled.
                } catch (final XdStorageException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    exceptions[0] = e;
                }
            });

            if (exceptions[0] != null)
                throw exceptions[0];

            if (conExceptions[0] != null)
                throw conExceptions[0];
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method load all objects of specified class.
     *
     * @param cl Class of objects.
     * @return Return map of objects.
     * @throws XdStorageException This exception will throw if error arises in loading process.
     */
    @Override
    public <T> Collection<T> load(final Class<T> cl) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<T> load(final Class<T> cl, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                return (Collection<T>) resource.read(tx);
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);

                final XdStorageException[] exceptions = new XdStorageException[]{null};
                final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
                final AtomicBoolean hasError = new AtomicBoolean(false);

                final Collection<Object> references = referencesResource.readReferences(tx);
                references.forEach(reference -> {
                    if (hasError.get()) {
                        return;
                    }

                    try {
                        final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(reference, tx);
                        resource.readByReference(reference, tx);
                    } catch (final XdStorageConnectionException e) {
                        if (hasError.get()) {
                            return;
                        }
                        hasError.set(true);
                        conExceptions[0] = e;
                    } catch (final XdStorageException e) {
                        if (hasError.get()) {
                            return;
                        }
                        hasError.set(true);
                        exceptions[0] = e;
                    }
                });

                if (exceptions[0] != null)
                    throw exceptions[0];

                if (conExceptions[0] != null)
                    throw conExceptions[0];

                return (Collection<T>) references;
            } else {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method load objects by specified predicate.
     *
     * @param cl        Class of objects.
     * @param predicate Predicate for filtering objects.
     * @return Return map of objects.
     * @throws XdStorageException This exception will throw if error arises in loading process.
     */
    @Override
    public <T> Collection<T> load(final Class<T> cl, final IXdStoragePredicate<T> predicate) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, predicate, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<T> load(final Class<T> cl, final IXdStoragePredicate<T> predicate, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (predicate == null)
            throw new XdStorageException("predicate cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
        if (!isTransactionAlive(transaction))
            throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

        if (!checkHasObjectIdField(cl))
            throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

        final XdStorageTransaction tx = cast(transaction);

        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
            return resource.read(tx, predicate);
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final Collection<T> result = new ArrayList<>();
            final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);

            final XdStorageException[] exceptions = new XdStorageException[]{null};
            final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);

            final Collection<Object> references = referencesResource.readReferences(tx);
            references.forEach(reference -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(reference, tx);
                    resource.readByReference(reference, tx);
                    if (predicate.passed((T) reference)) {
                        result.add((T) reference);
                    }
                } catch (final XdStorageConnectionException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    conExceptions[0] = e;
                } catch (final XdStorageException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    exceptions[0] = e;
                }
            });

            if (exceptions[0] != null)
                throw exceptions[0];

            if (conExceptions[0] != null)
                throw conExceptions[0];

            return result;
        } else {
            throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                    + XdStoragePolicy.StoreAsSingleObject);
        }
    }

    @Override
    public <T> Collection<T> load(final Class<T> cl, final String indexName, final XdStorageSearchQuery query) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, indexName, query, transaction);
    }

    @Override
    public <T> Collection<T> load(final Class<T> cl, final String indexName, final XdStorageSearchQuery query,
                                  final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (query == null)
            throw new XdStorageException("query cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy == XdStoragePolicy.StoreWithParentObject) {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }

            return services.getSearchManager().search(cl, indexName, query, tx);
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    @Override
    public long count(final Class<?> cl, final String indexName, final XdStorageSearchQuery query) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return count(cl, indexName, query, transaction);
    }

    @Override
    public long count(final Class<?> cl, final String indexName, final XdStorageSearchQuery query,
                      final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (query == null)
            throw new XdStorageException("query cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy == XdStoragePolicy.StoreWithParentObject) {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }

            return services.getSearchManager().count(cl, indexName, query, tx);
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method load objects by specified predicate.
     *
     * @param cl      Class of objects.
     * @param watcher Watcher for viewable objects.
     * @return Return map of objects.
     * @throws XdStorageException This exception will throw if error arises in loading process.
     */
    @Override
    public <T> void watch(final Class<T> cl, final IXdStorageWatcher<T> watcher) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        watch(cl, watcher, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void watch(final Class<T> cl, final IXdStorageWatcher<T> watcher, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (watcher == null)
            throw new XdStorageException("predicate cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                resource.watch(tx, watcher);
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);

                final XdStorageException[] exceptions = new XdStorageException[]{null};
                final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
                final AtomicBoolean hasError = new AtomicBoolean(false);

                final Collection<Object> references = referencesResource.readReferences(tx);
                references.forEach(reference -> {
                    if (hasError.get()) {
                        return;
                    }

                    try {
                        final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(reference, tx);
                        resource.watch(tx, watcher);
                    } catch (final XdStorageConnectionException e) {
                        if (hasError.get()) {
                            return;
                        }
                        hasError.set(true);
                        conExceptions[0] = e;
                    } catch (final XdStorageException e) {
                        if (hasError.get()) {
                            return;
                        }
                        hasError.set(true);
                        exceptions[0] = e;
                    }
                });

                if (exceptions[0] != null)
                    throw exceptions[0];

                if (conExceptions[0] != null)
                    throw conExceptions[0];
            } else {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method updates object.
     *
     * @param object Specified object
     * @throws XdStorageException This exception will be throw if an error arises in update
     *                            process.
     */
    @Override
    public void update(final Object object) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        update(object, transaction);
    }

    @Override
    public void update(final Object object, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (object == null)
            throw new XdStorageException("object cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
            final IXdStorageSearchManager searchManager = services.getSearchManager();

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                resource.update(object, tx);

                if (searchManager.hasIndex(object)) {
                    searchManager.update(object, tx);
                }
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(object, tx);
                resource.update(object, tx);

                if (searchManager.hasIndex(object)) {
                    searchManager.update(object, tx);
                }
            } else {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }
            // AUTOSAVE (docs/tasks/2.1.0/22): cascade @XdStorageAutoSave children in the same tx
            processAutoSave(object, tx);
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method updates objects.
     *
     * @param objects Specified objects
     * @throws XdStorageException This exception will be throw if an error arises in update
     *                            process.
     */
    @Override
    public void update(final Collection<?> objects) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        update(objects, transaction);
    }

    @Override
    public void update(final Collection<?> objects, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (objects == null)
            throw new XdStorageException("objects cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (objects.isEmpty())
                return;

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
            final IXdStorageSearchManager searchManager = services.getSearchManager();

            final XdStorageException[] exceptions = new XdStorageException[]{null};
            final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);

            objects.forEach(object -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
                    if (!checkHasObjectIdField(cl)) {
                        throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");
                    }

                    final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

                    final XdStoragePolicy policy = clInfo.getPolicy();
                    if (policy == XdStoragePolicy.StoreAsClassObjects) {
                        final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                        resource.update(object, tx);

                        if (searchManager.hasIndex(object)) {
                            searchManager.update(object, tx);
                        }
                    } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                        final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(object, tx);
                        resource.update(object, tx);

                        if (searchManager.hasIndex(object)) {
                            searchManager.update(object, tx);
                        }
                    } else {
                        throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                                + XdStoragePolicy.StoreAsSingleObject);
                    }
                } catch (final XdStorageConnectionException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    conExceptions[0] = e;
                } catch (final XdStorageException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    exceptions[0] = e;
                }
            });

            if (exceptions[0] != null)
                throw exceptions[0];

            if (conExceptions[0] != null)
                throw conExceptions[0];
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method deletes specified object by object's reference. This
     * reference can be full filled object.
     *
     * @param reference Specified reference.
     * @throws XdStorageException This exception will be throw if an error arises in delete
     *                            process.
     */
    @Override
    public void delete(final Object reference) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        delete(reference, transaction);
    }

    @Override
    public void delete(final Object reference, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (reference == null)
            throw new XdStorageException("reference cannot be null");
        log.debug("delete {} requested by tx {}", reference.getClass().getSimpleName(), transaction.getTransactionId());
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            final Class<?> cl = XdStorageObjectUtils.getEntityClass(reference.getClass());
            if (!checkHasObjectIdField(cl))
                throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
            final IXdStorageSearchManager searchManager = services.getSearchManager();

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy == XdStoragePolicy.StoreAsClassObjects) {
                if (searchManager.hasIndex(reference)) {
                    searchManager.delete(reference, tx);
                }

                final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                resource.delete(reference, tx);
            } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                if (searchManager.hasIndex(reference)) {
                    searchManager.delete(reference, tx);
                }

                final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);
                final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(reference, tx);
                referencesResource.deleteReference(reference, tx);
                resource.delete(reference, tx);
            } else {
                throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                        + XdStoragePolicy.StoreAsSingleObject);
            }

            log.debug("deleted {} by tx {}", cl.getSimpleName(), transaction.getTransactionId());
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    /**
     * This method deletes some objects.
     *
     * @param references Collection objects to remove.
     * @throws XdStorageException This exception will throw if an error arises in delete
     *                            process.
     */
    @Override
    public void delete(final Collection<?> references) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        delete(references, transaction);
    }

    @Override
    public void delete(final Collection<?> references, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        ensureOpen();
        if (references == null)
            throw new XdStorageException("references collection cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");

        final XdStorageTransactionManager txManager = (XdStorageTransactionManager) services.getTransactionsManager();
        txManager.incrementActiveThreads(transaction.getTransactionId());
        try {
            if (!isTransactionAlive(transaction))
                throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

            if (references.isEmpty())
                return;

            final XdStorageTransaction tx = cast(transaction);

            final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
            final IXdStorageSearchManager searchManager = services.getSearchManager();

            final XdStorageException[] exceptions = new XdStorageException[]{null};
            final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);

            references.forEach(reference -> {
                if (hasError.get()) {
                    return;
                }

                try {
                    final Class<?> cl = XdStorageObjectUtils.getEntityClass(reference.getClass());
                    if (!checkHasObjectIdField(cl)) {
                        throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");
                    }

                    final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

                    final XdStoragePolicy policy = clInfo.getPolicy();
                    if (policy == XdStoragePolicy.StoreAsClassObjects) {
                        if (searchManager.hasIndex(reference)) {
                            searchManager.delete(reference, tx);
                        }

                        final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
                        resource.delete(reference, tx);
                    } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
                        if (searchManager.hasIndex(reference)) {
                            searchManager.delete(reference, tx);
                        }

                        final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);
                        final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(reference, tx);
                        referencesResource.deleteReference(reference, tx);
                        resource.delete(reference, tx);
                    } else {
                        throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                                + XdStoragePolicy.StoreAsSingleObject);
                    }
                } catch (final XdStorageConnectionException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    conExceptions[0] = e;
                } catch (final XdStorageException e) {
                    if (hasError.get()) {
                        return;
                    }
                    hasError.set(true);
                    exceptions[0] = e;
                }
            });

            if (exceptions[0] != null)
                throw exceptions[0];

            if (conExceptions[0] != null)
                throw conExceptions[0];
        } finally {
            txManager.decrementActiveThreads(transaction.getTransactionId());
        }
    }

    @Override
    public IXdStorageTransactionManager getTransactionManager() {
        ensureOpen();
        return services.getTransactionsManager();
    }

    @Override
    public void shutdown() {
        synchronized (shutdownLock) {
            // S-4: shutdown идемпотентен — повторный вызов (явный + хук) ничего не ломает
            if (closed)
                return;
            closed = true;
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (final IllegalStateException e) {
                // JVM уже в процессе завершения — хук сейчас сам и сработает
            } catch (final Throwable t) {
                log.warn("shutdown hook of storage '{}' could not be removed: {}", name, t.getMessage());
            }
            shutdownHook = null;
        }
        XdStorageProvider.unregisterStorage(this);
        services.shutdown();
        if (folderLock != null) {
            folderLock.close();
            folderLock = null;
        }
    }

    private boolean checkHasObjectIdField(final Class<?> cl) {
        return XdStorageObjectUtils.getClassInfo(cl).getIdField() != null;
    }

    private static <T> T cast(final Object value) throws XdStorageException {
        try {
            return (T) value;
        } catch (final Throwable t) {
            throw new XdStorageException(t);
        }
    }

    /** AUTOSAVE: the plain INSERT dispatch of the public save (no cascade) — shared by the
     * facade entry and the cascade engine, so cascaded children walk the identical per-object
     * path (id generation backfills the same instance; triggers fire at the commit phase). */
    private void persistObject(final Object object, final IXdStorageTransaction transaction)
            throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        if (!checkHasObjectIdField(cl))
            throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

        final XdStorageTransaction tx = cast(transaction);

        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
        final IXdStorageSearchManager searchManager = services.getSearchManager();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
            resource.insert(object, tx);

            if (searchManager.hasIndex(object)) {
                searchManager.insert(object, tx);
            }
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);
            referencesResource.insertReference(object, tx);

            final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(object, tx);
            resource.insert(object, tx);

            if (searchManager.hasIndex(object)) {
                searchManager.insert(object, tx);
            }
        } else {
            throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                    + XdStoragePolicy.StoreAsSingleObject);
        }
    }

    /** AUTOSAVE: the plain UPDATE dispatch of the public update (no cascade). */
    private void updateObject(final Object object, final IXdStorageTransaction transaction)
            throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        if (!checkHasObjectIdField(cl))
            throw new XdStorageException("the class " + cl + " must have  identifier field @XdStorageObjectId");

        final XdStorageTransaction tx = cast(transaction);

        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
        final IXdStorageSearchManager searchManager = services.getSearchManager();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            final IXdStorageIndexDaoResource resource = resourcesManager.lockIndexResource(clInfo, tx);
            resource.update(object, tx);

            if (searchManager.hasIndex(object)) {
                searchManager.update(object, tx);
            }
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageDaoResource resource = resourcesManager.lockObjectResource(object, tx);
            resource.update(object, tx);

            if (searchManager.hasIndex(object)) {
                searchManager.update(object, tx);
            }
        } else {
            throw new XdStorageException("class " + cl.getName() + " must have policy " + XdStoragePolicy.StoreAsClassObjects + " or "
                    + XdStoragePolicy.StoreAsSingleObject);
        }
    }

    // =========================================================================
    // AUTOSAVE (docs/tasks/2.1.0/22): @XdStorageAutoSave cascade-save. The cascade runs
    // AFTER the parent's own persistence, inside the caller's transaction. Persist and
    // cascade are SEPARATE phases: persistObject/updateObject is the plain policy dispatch
    // (with the id-field guard), and processAutoSave walks the @XdStorageAutoSave fields of
    // each persisted object through the once-per-operation visited set (IDENTITY-based,
    // shared through the thread locale across the whole cascade — that is what makes
    // reference cycles terminate and duplicates dedup). Child triggers, observers, id
    // generation, rollback and MVCC behave exactly as for explicit saves.
    // =========================================================================

    private static final ThreadLocal<Set<Object>> AUTO_SAVE_VISITED = new ThreadLocal<>();

    private static final ThreadLocal<Integer> AUTO_SAVE_DEPTH = ThreadLocal.withInitial(() -> 0);

    private static final Map<Class<?>, List<java.lang.reflect.Field>> AUTO_SAVE_FIELD_CACHE =
            new ConcurrentHashMap<>();

    private static final Set<Class<?>> AUTO_SAVE_EMBEDDED_WARNED =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());

    /**
     * The cascade entry, called at the tail of the public save/update dispatches. The
     * visited set lives for exactly ONE top-level save/update (the depth counter detects
     * the outermost call of a cascade and clears the set at its end) — a set that leaked
     * across operations would silently skip every later cascade on the same thread.
     */
    private void processAutoSave(final Object object, final IXdStorageTransaction tx)
            throws XdStorageException, XdStorageConnectionException {
        final boolean outermost = AUTO_SAVE_DEPTH.get() == 0;
        AUTO_SAVE_DEPTH.set(AUTO_SAVE_DEPTH.get() + 1);
        try {
            Set<Object> visited = AUTO_SAVE_VISITED.get();
            if (visited == null) {
                visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                AUTO_SAVE_VISITED.set(visited);
            }
            cascadeAutoSaveFields(object, tx, visited);
        } finally {
            final int depth = AUTO_SAVE_DEPTH.get() - 1;
            AUTO_SAVE_DEPTH.set(depth);
            if (depth == 0) {
                AUTO_SAVE_VISITED.remove();
            }
        }
    }

    /** Marks the object processed and cascade-upserts its annotated children (recursively). */
    /** AUTOSAVE: the upsert half of the cascade — id null → insert (generated id backfills
     * the same instance); id set → update if the index knows it, insert otherwise. */
    private void persistOrUpsertObject(final Object child, final IXdStorageTransaction transaction)
            throws XdStorageException, XdStorageConnectionException {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(child.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final Object id = clInfo.getIdField() != null ? clInfo.getIdField().get(child) : null;
        if (id == null) {
            persistObject(child, transaction);
            return;
        }
        final XdStorageTransaction tx = cast(transaction);
        final IXdStorageIndexDaoResource indexResource =
                services.getResourcesManager().lockIndexResource(clInfo, tx);
        if (indexResource.has(id, tx)) {
            updateObject(child, tx);
        } else {
            persistObject(child, tx);
        }
    }

    private void cascadeAutoSaveFields(final Object object, final IXdStorageTransaction tx,
                                       final Set<Object> visited)
            throws XdStorageException, XdStorageConnectionException {
        final Object unwrapped = XdStorageObjectUtils.getWrappedObjectOrSameObject(object);
        if (!visited.add(unwrapped)) {
            return; // fully processed earlier in this operation (dedup + cycle guard)
        }
        final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(unwrapped.getClass());
        for (final java.lang.reflect.Field field : autoSaveFields(entityClass)) {
            for (final Object child : autoSaveChildren(unwrapped, field)) {
                final Object childUnwrapped = XdStorageObjectUtils.getWrappedObjectOrSameObject(child);
                if (!visited.add(childUnwrapped)) {
                    continue; // the same instance via two fields, or a cycle back-edge
                }
                final Class<?> childClass = XdStorageObjectUtils.getEntityClass(childUnwrapped.getClass());
                final XdStoragePolicy childPolicy = XdStorageObjectUtils.getClassInfo(childClass).getPolicy();
                if (childPolicy == XdStoragePolicy.StoreWithParentObject) {
                    // the parent's own write already embeds StoreWithParentObject children —
                    // a cascade upsert would fail (no independent resource); WARN once per class
                    if (AUTO_SAVE_EMBEDDED_WARNED.add(childClass)) {
                        log.warn("@XdStorageAutoSave field element {} uses policy StoreWithParentObject — "
                                + "already embedded in the parent's file; cascade-save is a no-op for it",
                                childClass.getName());
                    }
                    continue;
                }
                persistOrUpsertObject(childUnwrapped, tx);
                cascadeAutoSaveFields(childUnwrapped, tx, visited);
            }
        }
    }

    /** The @XdStorageAutoSave fields of an entity class, inherited ones included (cached). */
    private static List<java.lang.reflect.Field> autoSaveFields(final Class<?> entityClass) {
        return AUTO_SAVE_FIELD_CACHE.computeIfAbsent(entityClass, cl -> {
            final List<java.lang.reflect.Field> result = new ArrayList<>();
            for (Class<?> c = cl; c != null && c != Object.class; c = c.getSuperclass()) {
                for (final java.lang.reflect.Field field : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (field.isAnnotationPresent(io.github.xdstorage.annotations.XdStorageAutoSave.class)) {
                        field.setAccessible(true);
                        result.add(field);
                    }
                }
            }
            return result;
        });
    }

    /** The cascade candidates of one annotated field: collection elements or a single reference. */
    private static List<Object> autoSaveChildren(final Object unwrapped, final java.lang.reflect.Field field) {
        final List<Object> children = new ArrayList<>();
        final Object value;
        try {
            value = field.get(unwrapped);
        } catch (final IllegalAccessException e) {
            throw new XdStorageRuntimeException("cannot read @XdStorageAutoSave field " + field.getName(), e);
        }
        if (value == null) {
            return children;
        }
        if (value instanceof Collection) {
            for (final Object element : (Collection<?>) value) {
                if (element != null) {
                    children.add(XdStorageObjectUtils.getWrappedObjectOrSameObject(element));
                }
            }
        } else if (value.getClass().isArray()) {
            // v1 scope: collections and single references — arrays are deliberately not cascaded
        } else {
            children.add(XdStorageObjectUtils.getWrappedObjectOrSameObject(value));
        }
        return children;
    }

}
