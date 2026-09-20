package org.flib.xdstorage;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.resource.*;
import org.flib.xdstorage.search.IXdStorageSearchManager;
import org.flib.xdstorage.search.query.IXdStorageCriterion;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.structure.update.IXdStorageStructureUpdater;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.trigger.IXdStorageTrigger;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class presents simple working interface. This one provides simple
 * methods for object persistence.
 */
class XdStorage implements IXdFileStorage, IXdSqlStorage {

    private final String name;

    private final XdStorageServicesLocator services;

    /**
     * This constructor initialize storage for specified folder. All files will be
     * storaged in this folder.
     *
     * @param folder Storage path.
     */
    XdStorage(final String name, final String folder, final int fragmentSize) {
        this.name = name;
        services = new XdStorageServicesLocator();
        services.initFileConfiguration(this, folder, fragmentSize);
    }

    /**
     *
     */
    XdStorage(final String name) {
        this.name = name;
        services = new XdStorageServicesLocator();
        services.initPGConfiguration(this);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * This method registers trigger. Every trigger will be executed in change
     * transaction and must work only with parameter's object.
     *
     * @param trigger This trigger will be registered.
     */
    @Override
    public <T> void registerTrigger(final IXdStorageTrigger<T> trigger) {
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
        return services.getTransactionsManager().beginTransaction(timeout);
    }

    @Override
    public IXdStorageTransaction beginTransaction() {
        return beginTransaction(10 * 1000);
    }

    @Override
    public IXdStorageTransaction beginTransaction(final IXdStorageTransaction transaction, final long timeout) {
        return services.getTransactionsManager().beginTransaction(transaction, timeout);
    }

    @Override
    public IXdStorageTransaction beginTransaction(final IXdStorageTransaction transaction) {
        return beginTransaction(transaction, 10 * 1000);
    }

    /**
     * This method commit specified transaction.
     *
     * @param transaction Specified transaction
     */
    @Override
    public void commitTransaction(final IXdStorageTransaction transaction) throws XdStorageException {
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
        services.getStructureManager().registerUpdater(updater);
    }

    /**
     * @param cl
     * @throws XdStorageException
     */
    @Override
    public List<IXdStorageStructureUpdater> executeStructureUpdate(final Class<?> cl) throws XdStorageException, XdStorageConnectionException {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        save(object, transaction);
    }

    @Override
    public void save(final Object object, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (object == null)
            throw new XdStorageException("object cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
        if (!isTransactionAlive(transaction))
            throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

        final Class<?> cl = object.getClass();
        if (!checkHasObjectIdField(cl))
            throw new XdStorageException("the class " + cl + " must have identifier field @XdStorageObjectId");

        final XdStorageTransaction tx = cast(transaction);

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();
        final IXdStorageSearchManager searchManager = services.getSearchManager();

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

    /**
     * This method saves collection of objects.
     *
     * @param objects Collection of objects.
     * @throws XdStorageException This exception will be throw if error arises in save
     *                            operation.
     */
    @Override
    public void save(final Collection<?> objects) throws XdStorageException, XdStorageConnectionException {
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        save(objects, transaction);
    }

    @Override
    public void save(final Collection<?> objects, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (objects == null)
            throw new XdStorageException("objects collection cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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

        objects.stream().forEach(object -> {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return has(cl, id, transaction);
    }

    @Override
    public boolean has(final Class<?> cl, final Object id, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        load(reference, transaction);
    }

    @Override
    public void load(final Object reference, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (reference == null)
            throw new XdStorageException("reference cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, id, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T load(final Class<T> cl, final Object id, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        load(references, transaction);
    }

    @Override
    public void load(final Collection<?> references, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (references == null)
            throw new XdStorageException("references collection cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
        if (!isTransactionAlive(transaction))
            throw new XdStorageException("transaction " + transaction.getTransactionId() + " is not alive");

        if (references.isEmpty())
            return;

        final XdStorageTransaction tx = cast(transaction);

        final XdStorageAbstractResourcesManager resourcesManager = services.getResourcesManager();

        final XdStorageException[] exceptions = new XdStorageException[]{null};
        final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
        final AtomicBoolean hasError = new AtomicBoolean(false);

        references.stream().forEach(reference -> {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<T> load(final Class<T> cl, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (cl == null)
            throw new XdStorageException("class cannot be null");
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
            return (Collection<T>) resource.read(tx);
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);

            final XdStorageException[] exceptions = new XdStorageException[]{null};
            final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);

            final Collection<Object> references = referencesResource.readReferences(tx);
            references.stream().forEach(reference -> {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, predicate, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Collection<T> load(final Class<T> cl, final IXdStoragePredicate<T> predicate, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
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
            references.stream().forEach(reference -> {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, indexName, query, transaction);
    }

    @Override
    public <T> Collection<T> load(final Class<T> cl, final String indexName, final XdStorageSearchQuery query,
                                  final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (query == null)
            throw new XdStorageException("query cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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
    }

    @Override
    public <T> Collection<T> load(final Class<T> cl, final String indexName, final XdStorageSqlSearchQuery query) throws XdStorageException, XdStorageConnectionException {
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        return load(cl, indexName, query, transaction);
    }

    @Override
    public <T> Collection<T> load(final Class<T> cl, final String indexName, final XdStorageSqlSearchQuery query,
                                  final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (query == null)
            throw new XdStorageException("query cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        watch(cl, watcher, transaction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void watch(final Class<T> cl, final IXdStorageWatcher<T> watcher, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (cl == null)
            throw new XdStorageException("class cannot be null");
        if (watcher == null)
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
            resource.watch(tx, watcher);
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final IXdStorageDaoResource referencesResource = resourcesManager.lockReferencesResource(clInfo, tx);

            final XdStorageException[] exceptions = new XdStorageException[]{null};
            final XdStorageConnectionException[] conExceptions = new XdStorageConnectionException[]{null};
            final AtomicBoolean hasError = new AtomicBoolean(false);

            final Collection<Object> references = referencesResource.readReferences(tx);
            references.stream().forEach(reference -> {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        update(object, transaction);
    }

    @Override
    public void update(final Object object, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (object == null)
            throw new XdStorageException("object cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        update(objects, transaction);
    }

    @Override
    public void update(final Collection<?> objects, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (objects == null)
            throw new XdStorageException("objects cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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

        objects.stream().forEach(object -> {
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        delete(reference, transaction);
    }

    @Override
    public void delete(final Object reference, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (reference == null)
            throw new XdStorageException("reference cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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
        if (!checkIsInTransaction())
            throw new XdStorageException("method must be executed in transaction");

        final IXdStorageTransaction transaction = services.getTransactionsManager().getCurrentTransaction();

        delete(references, transaction);
    }

    @Override
    public void delete(final Collection<?> references, final IXdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        if (references == null)
            throw new XdStorageException("references collection cannot be null");
        if (transaction == null)
            throw new XdStorageException("transaction cannot be null");
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

        references.stream().forEach(reference -> {
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
    }

    public void shutdown() {
        XdStorageProvider.unregisterStorage(this);
        services.shutdown();
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
}
