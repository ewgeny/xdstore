package org.flib.xdstore;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.flib.xdstore.serialization.IXmlDataStoreIOFactory;
import org.flib.xdstore.transaction.XmlDataStoreAnnotatedIndexResource;
import org.flib.xdstore.transaction.XmlDataStoreAnnotatedResource;
import org.flib.xdstore.transaction.XmlDataStoreDeleteException;
import org.flib.xdstore.transaction.XmlDataStoreIndexResource;
import org.flib.xdstore.transaction.XmlDataStoreInsertException;
import org.flib.xdstore.transaction.XmlDataStoreReadException;
import org.flib.xdstore.transaction.XmlDataStoreResource;
import org.flib.xdstore.transaction.XmlDataStoreResourcesManager;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;
import org.flib.xdstore.transaction.XmlDataStoreTransactionsManager;
import org.flib.xdstore.transaction.XmlDataStoreUpdateException;
import org.flib.xdstore.trigger.IXmlDataStoreTrigger;
import org.flib.xdstore.trigger.XmlDataStoreTriggerManager;
import org.flib.xdstore.utils.ObjectUtils;
import org.flib.xdstore.utils.StringUtils;

/**
 * This class presents simple working interface. This one provides simple
 * methods for object persistence.
 * 
 * @author Евгений
 * 
 */
public class XmlDataStore {

	private final String                            folder;

	private final Map<Class<?>, XmlDataStorePolicy> policies;

	private final XmlDataStoreTransactionsManager   transactionsManager;

	private final XmlDataStoreTriggerManager        triggersManager;

	private final XmlDataStoreResourcesManager      resourcesManager;

	private boolean                                 useFragmentation;

	/**
	 * This constructor initialize store for specified folder. All files will be
	 * stored in this folder.
	 * 
	 * @param folder
	 *            Store path.
	 */
	public XmlDataStore(final String folder) {
		if (StringUtils.isBlank(folder))
			throw new IllegalArgumentException("store folder's name cannot be blank");

		this.folder = folder;
		this.policies = new HashMap<Class<?>, XmlDataStorePolicy>();
		this.transactionsManager = new XmlDataStoreTransactionsManager();
		this.useFragmentation = false;
		this.triggersManager = new XmlDataStoreTriggerManager();
		this.resourcesManager = new XmlDataStoreResourcesManager(folder, triggersManager, this.policies, 10);
	}

	/**
	 * This constructor initialize store for specified folder. All files will be
	 * stored in this folder. This constructor enables fragmentation.
	 * 
	 * @param folder
	 *            Store path.
	 * @param fragmentSize
	 *            Max count objects in one fragment file. Fragmentation works
	 *            only with policy ClassObjectsFile and doesn't work with root
	 *            objects.
	 */
	public XmlDataStore(final String folder, final int fragmentSize) {
		if (StringUtils.isBlank(folder))
			throw new IllegalArgumentException("store folder's name cannot be blank");

		this.folder = folder;
		this.policies = new HashMap<Class<?>, XmlDataStorePolicy>();
		this.transactionsManager = new XmlDataStoreTransactionsManager();
		this.useFragmentation = true;
		this.triggersManager = new XmlDataStoreTriggerManager();
		this.resourcesManager = new XmlDataStoreResourcesManager(folder, triggersManager, this.policies, fragmentSize);
	}

	/**
	 * This method gets and return store folder.
	 * 
	 * @return Return store folder.
	 */
	public String getFolder() {
		return folder;
	}

	/**
	 * Use this method for registering store policy for specified class.
	 * 
	 * @param cl
	 *            Serializable class cannot be null.
	 * @param policy
	 *            Policy for specified class cannot be null.
	 */
	public void setStorePolicy(final Class<?> cl, final XmlDataStorePolicy policy) {
		if (cl == null)
			throw new IllegalArgumentException("class parameter cannot be null");
		if (policy == null)
			throw new IllegalArgumentException("policy parameter cannot be null");

		policies.put(cl, policy);
	}

	/**
	 * Get and return policy of serializable class, or return null if policy not
	 * registered.
	 * 
	 * @param cl
	 *            Specified class
	 * @return Return policy for specified class.
	 */
	public XmlDataStorePolicy getStorePolicy(final Class<?> cl) {
		if (cl == null)
			throw new IllegalArgumentException("class parameter cannot be null");

		return policies.get(cl);
	}

	/**
	 * Set factory for creation writer and reader.
	 * 
	 * @param factory
	 *            Readers and writers provider factory.
	 */
	public void setIOFactory(final IXmlDataStoreIOFactory factory) {
		if (factory == null)
			throw new IllegalArgumentException("factory cannot be null");

		resourcesManager.setIOFactory(factory);
	}

	/**
	 * This method registers trigger. Every trigger will be executed in change
	 * transaction and must work only with parameter's object.
	 * 
	 * @param trigger
	 *            This trigger will be registered.
	 */
	public <T> void registerTrigger(final IXmlDataStoreTrigger<T> trigger) {
		triggersManager.registerTrigger(trigger);
	}

	/**
	 * This method starts new transaction.
	 * 
	 * @return Return started transaction.
	 */
	public XmlDataStoreTransaction beginTransaction() {
		return transactionsManager.beginTransaction();
	}

	/**
	 * This method commit specified transaction.
	 * 
	 * @param transaction
	 *            Specified transaction
	 */
	public void commitTransaction(final XmlDataStoreTransaction transaction) {
		if (transaction == null)
			throw new IllegalArgumentException("transaction cannot be null");

		transactionsManager.commitTransaction(transaction);
	}

	/**
	 * This method roll back specified transaction.
	 * 
	 * @param transaction
	 *            Specified transaction
	 */
	public void rollbackTransaction(final XmlDataStoreTransaction transaction) {
		if (transaction == null)
			throw new IllegalArgumentException("transaction cannot be null");

		transactionsManager.rollbackTransaction(transaction);
	}

	/**
	 * This method checks transaction.
	 * 
	 * @return Return true if transaction has been started.
	 */
	private boolean checkIsInTransaction() {
		return transactionsManager.getTransaction() != null;
	}
	
	/**
	 * This method saves specified object.
	 * 
	 * @param object
	 *            Specified object
	 * @throws XmlDataStoreInsertException
	 *             This exception will be throw if an error arises in save
	 *             operation.
	 */
	public <T extends IXmlDataStoreIdentifiable> void saveObject(final T object) throws XmlDataStoreInsertException {
		if (object == null)
			throw new XmlDataStoreRuntimeException("object cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = object.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				resource.insertObject(object, transaction);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				resource.insertObject(object, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(object, transaction);

			referencesResource.insertReference(object, transaction);
			resource.insertObject(object, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method saves collection of objects.
	 * 
	 * @param objects
	 *            Collection of objects.
	 * @throws XmlDataStoreInsertException
	 *             This exception will be throw if error arises in save
	 *             operation.
	 */
	public <T extends IXmlDataStoreIdentifiable> void saveObjects(final Collection<T> objects) throws XmlDataStoreInsertException {
		if (objects == null)
			throw new XmlDataStoreRuntimeException("objects collection cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		if (objects.size() == 0)
			return;

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = objects.iterator().next().getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				for (final T object : objects) {
					resource.insertObject(object, transaction);
				}
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				for (final T object : objects) {
					resource.insertObject(object, transaction);
				}
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);
			for (final T object : objects) {
				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(object, transaction);
				referencesResource.insertReference(object, transaction);
				resource.insertObject(object, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}
	
	/**
	 * This check existing object by specified identifier.
	 * 
	 * @param cl Specified class
	 * @param id Specified identifier
	 * @return Return true if and only if this object contains in the store.
	 */
	public <T extends IXmlDataStoreIdentifiable> boolean hasObject(final Class<T> cl, final String id) {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (StringUtils.isBlank(id))
			throw new XmlDataStoreRuntimeException("id cannot be blank");
		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		
		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				return resource.hasObject(id, transaction);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				return resource.hasObject(id, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(cl, id, transaction);
			return resource.hasObject(id, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method loads required object by object's reference.
	 * 
	 * @param reference
	 *            Specified object's reference.
	 * @throws XmlDataStoreReadException
	 *             This exception will be throw if error arises in load
	 *             operation.
	 */
	public <T extends IXmlDataStoreIdentifiable> void loadObject(final T reference) throws XmlDataStoreReadException {
		if (reference == null)
			throw new XmlDataStoreRuntimeException("reference cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = reference.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				resource.readObjectByReference(reference, transaction);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				resource.readObjectByReference(reference, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);
			resource.readObjectByReference(reference, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method loads object by specified identifier.
	 * 
	 * @param cl
	 *            Specified class
	 * @param id
	 *            Specified identifier
	 * @return Return object if exist, else will be throw exception
	 * @throws XmlDataStoreReadException
	 *             This exception will be throw if object isn't exist.
	 */
	@SuppressWarnings("unchecked")
	public <T extends IXmlDataStoreIdentifiable> T loadObject(final Class<T> cl, final String id) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (StringUtils.isBlank(id))
			throw new XmlDataStoreRuntimeException("id cannot be blank");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				return (T) resource.readObject(id, transaction);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				return (T) resource.readObject(id, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(cl, id, transaction);
			return (T) resource.readObject(id, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method loads some objects in specified references collection.
	 * 
	 * @param references
	 *            Collection of objects' references.
	 * @return Return true if all objects from collection was been loaded
	 *         successful, else return false.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	public <T extends IXmlDataStoreIdentifiable> boolean loadObjects(final Collection<T> references) throws XmlDataStoreReadException {
		if (references == null)
			throw new XmlDataStoreRuntimeException("references collection cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		if (references.size() == 0)
			return false;

		boolean result = true;
		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = references.iterator().next().getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				for (final T reference : references) {
					resource.readObjectByReference(reference, transaction);
				}
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				for (final T reference : references) {
					resource.readObjectByReference(reference, transaction);
				}
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			for (final T reference : references) {
				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);
				resource.readObjectByReference(reference, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
		return result;
	}

	/**
	 * This method load all objects of specified class.
	 * 
	 * @param cl
	 *            Class of objects.
	 * @param predicate
	 *            Predicate for filtering objects.
	 * @return Return map of objects.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	@SuppressWarnings("unchecked")
	public <T extends IXmlDataStoreIdentifiable> Map<String, T> loadObjects(final Class<T> cl) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				return (Map<String, T>) resource.readObjects(transaction);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				return (Map<String, T>) resource.readObjects(transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final Map<String, T> result = new HashMap<String, T>();
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);

			final Map<String, IXmlDataStoreIdentifiable> references = referencesResource.readReferences(transaction);
			for (final Map.Entry<String, IXmlDataStoreIdentifiable> entry : references.entrySet()) {
				final IXmlDataStoreIdentifiable reference = entry.getValue();

				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);
				resource.readObjectByReference(reference, transaction);

				result.put(reference.getDataStoreId(), (T) reference);
			}
			return result;
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method load objects by specified predicate.
	 * 
	 * @param cl
	 *            Class of objects.
	 * @param predicate
	 *            Predicate for filtering objects.
	 * @return Return map of objects.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	@SuppressWarnings("unchecked")
	public <T extends IXmlDataStoreIdentifiable> Map<String, T> loadObjects(final Class<T> cl, final IXmlDataStorePredicate<T> predicate) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (predicate == null)
			throw new XmlDataStoreRuntimeException("predicate cannot be null");
		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				return resource.readObjects(transaction, predicate);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				return resource.readObjects(transaction, predicate);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final Map<String, T> result = new HashMap<String, T>();
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);

			final Map<String, IXmlDataStoreIdentifiable> references = referencesResource.readReferences(transaction);
			for (final Map.Entry<String, IXmlDataStoreIdentifiable> entry : references.entrySet()) {
				final IXmlDataStoreIdentifiable reference = entry.getValue();

				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);
				resource.readObjectByReference(reference, transaction);
				if (predicate.passed((T) reference)) {
					result.put(reference.getDataStoreId(), (T) reference);
				}
			}
			return result;
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method updates object.
	 * 
	 * @param object
	 *            Specified object
	 * @throws XmlDataStoreUpdateException
	 *             This exception will be throw if an error arises in update
	 *             process.
	 */
	public <T extends IXmlDataStoreIdentifiable> void updateObject(final T object) throws XmlDataStoreUpdateException {
		if (object == null)
			throw new XmlDataStoreRuntimeException("object cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = object.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				resource.updateObject(object, transaction);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				resource.updateObject(object, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(object, transaction);
			resource.updateObject(object, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method deletes specified object by object's reference. This
	 * reference can be full filled object.
	 * 
	 * @param reference
	 *            Specified reference.
	 * @throws XmlDataStoreDeleteException
	 *             This exception will be throw if an error arises in delete
	 *             process.
	 */
	public <T extends IXmlDataStoreIdentifiable> void deleteObject(final T reference) throws XmlDataStoreDeleteException {
		if (reference == null)
			throw new XmlDataStoreRuntimeException("reference cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = reference.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				resource.deleteObject(reference, transaction);
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				resource.deleteObject(reference, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);
			referencesResource.deleteReference(reference, transaction);
			resource.deleteObject(reference, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method deletes some object.
	 * 
	 * @param references
	 *            Collection objects to remove.
	 * @throws XmlDataStoreDeleteException
	 *             This exception will throw if an error arises in delete
	 *             process.
	 */
	public <T extends IXmlDataStoreIdentifiable> void deleteObjects(final Collection<T> references) throws XmlDataStoreDeleteException {
		if (references == null)
			throw new XmlDataStoreRuntimeException("references collection cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		if (references.size() == 0)
			return;

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = references.iterator().next().getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreIndexResource resource = resourcesManager.lockIndexResource(cl, transaction);
				for (final T reference : references) {
					resource.deleteObject(reference, transaction);
				}
			} else {
				final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
				for (final T reference : references) {
					resource.deleteObject(reference, transaction);
				}
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);
			for (final T reference : references) {
				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);
				referencesResource.deleteReference(reference, transaction);
				resource.deleteObject(reference, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method saves specified object.
	 * 
	 * @param object
	 *            Specified object
	 * @throws XmlDataStoreInsertException
	 *             This exception will be throw if an error arises in save
	 *             operation.
	 */
	@SuppressWarnings("unchecked")
	public <T> void saveAnnotatedObject(final T object) throws XmlDataStoreInsertException {
		if (object == null)
			throw new XmlDataStoreRuntimeException("object cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final Class<T> cl = (Class<T>) object.getClass();
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				resource.insertObject(object, transaction);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				resource.insertObject(object, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource referencesResource = resourcesManager.lockAnnotatedReferencesResource(cl, field, transaction);
			final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(object, field, transaction);

			referencesResource.insertReference(object, transaction);
			resource.insertObject(object, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method saves collection of objects.
	 * 
	 * @param objects
	 *            Collection of objects.
	 * @throws XmlDataStoreInsertException
	 *             This exception will be throw if error arises in save
	 *             operation.
	 */
	public <T> void saveAnnotatedObjects(final Collection<T> objects) throws XmlDataStoreInsertException {
		if (objects == null)
			throw new XmlDataStoreRuntimeException("objects collection cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		if (objects.size() == 0)
			return;
		final Class<?> cl = objects.iterator().next().getClass();
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				for (final T object : objects) {
					resource.insertObject(object, transaction);
				}
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				for (final T object : objects) {
					resource.insertObject(object, transaction);
				}
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource referencesResource = resourcesManager.lockAnnotatedReferencesResource(cl, field, transaction);
			for (final T object : objects) {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(object, field, transaction);
				referencesResource.insertReference(object, transaction);
				resource.insertObject(object, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}
	
	/**
	 * This check existing object by specified identifier.
	 * 
	 * @param cl
	 *            Specified class
	 * @param id
	 *            Specified identifier
	 * @return Return true if and only if object contains in the store
	 */
	public <T> boolean hasAnnotatedObject(final Class<T> cl, final Object id) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (id == null)
			throw new XmlDataStoreRuntimeException("id cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				return resource.hasObject(id, transaction);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				return resource.hasObject(id, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(cl, field, id, transaction);
			return resource.hasObject(id, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method loads required object by object's reference.
	 * 
	 * @param reference
	 *            Specified object's reference.
	 * @throws XmlDataStoreReadException
	 *             This exception will be throw if error arises in load
	 *             operation.
	 */
	public <T> void loadAnnotatedObject(final T reference) throws XmlDataStoreReadException {
		if (reference == null)
			throw new XmlDataStoreRuntimeException("reference cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final Class<?> cl = reference.getClass();
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				resource.readObjectByReference(reference, transaction);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				resource.readObjectByReference(reference, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(reference, field, transaction);
			resource.readObjectByReference(reference, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method loads object by specified identifier.
	 * 
	 * @param cl
	 *            Specified class
	 * @param id
	 *            Specified identifier
	 * @return Return object if exist, else will be throw exception
	 * @throws XmlDataStoreReadException
	 *             This exception will be throw if object isn't exist.
	 */
	@SuppressWarnings("unchecked")
	public <T> T loadAnnotatedObject(final Class<T> cl, final Object id) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (id == null)
			throw new XmlDataStoreRuntimeException("id cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				return (T) resource.readObject(id, transaction);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				return (T) resource.readObject(id, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(cl, field, id, transaction);
			return (T) resource.readObject(id, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method loads some objects in specified references collection.
	 * 
	 * @param references
	 *            Collection of objects' references.
	 * @return Return true if all objects from collection was been loaded
	 *         successful, else return false.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	public <T> boolean loadAnnotatedObjects(final Collection<T> references) throws XmlDataStoreReadException {
		if (references == null)
			throw new XmlDataStoreRuntimeException("references collection cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		if (references.size() == 0)
			return false;
		final Class<?> cl = references.iterator().next().getClass();
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		boolean result = true;
		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				for (final T reference : references) {
					resource.readObjectByReference(reference, transaction);
				}
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				for (final T reference : references) {
					resource.readObjectByReference(reference, transaction);
				}
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			for (final T reference : references) {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(reference, field, transaction);
				resource.readObjectByReference(reference, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
		return result;
	}

	/**
	 * This method load all objects of specified class.
	 * 
	 * @param cl
	 *            Class of objects.
	 * @param predicate
	 *            Predicate for filtering objects.
	 * @return Return map of objects.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	@SuppressWarnings("unchecked")
	public <T> Map<Object, T> loadAnnotatedObjects(final Class<T> cl) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				return (Map<Object, T>) resource.readObjects(transaction);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				return (Map<Object, T>) resource.readObjects(transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final Map<Object, T> result = new HashMap<Object, T>();
			final XmlDataStoreAnnotatedResource referencesResource = resourcesManager.lockAnnotatedReferencesResource(cl, field, transaction);

			final Map<Object, Object> references = referencesResource.readReferences(transaction);
			for (final Map.Entry<Object, Object> entry : references.entrySet()) {
				final Object reference = entry.getValue();

				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(reference, field, transaction);
				resource.readObjectByReference(reference, transaction);

				result.put(field.getObjectId(reference), (T) reference);
			}
			return result;
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method load objects by specified predicate.
	 * 
	 * @param cl
	 *            Class of objects.
	 * @param predicate
	 *            Predicate for filtering objects.
	 * @return Return map of objects.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	@SuppressWarnings("unchecked")
	public <T> Map<Object, T> loadAnnotatedObjects(final Class<T> cl, final IXmlDataStoreAnnotatedPredicate<T> predicate) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (predicate == null)
			throw new XmlDataStoreRuntimeException("predicate cannot be null");
		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				return resource.readObjects(transaction, predicate);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				return resource.readObjects(transaction, predicate);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final Map<Object, T> result = new HashMap<Object, T>();
			final XmlDataStoreAnnotatedResource referencesResource = resourcesManager.lockAnnotatedReferencesResource(cl, field, transaction);

			final Map<Object, Object> references = referencesResource.readReferences(transaction);
			for (final Map.Entry<Object, Object> entry : references.entrySet()) {
				final Object reference = entry.getValue();

				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(reference, field, transaction);
				resource.readObjectByReference(reference, transaction);
				if (predicate.passed((T) reference)) {
					result.put(field.getObjectId(reference), (T) reference);
				}
			}
			return result;
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method updates object.
	 * 
	 * @param object
	 *            Specified object
	 * @throws XmlDataStoreUpdateException
	 *             This exception will be throw if an error arises in update
	 *             process.
	 */
	public <T> void updateAnnotatedObject(final T object) throws XmlDataStoreUpdateException {
		if (object == null)
			throw new XmlDataStoreRuntimeException("object cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		final Class<?> cl = object.getClass();
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				resource.updateObject(object, transaction);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				resource.updateObject(object, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(object, field, transaction);
			resource.updateObject(object, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method deletes specified object by object's reference. This
	 * reference can be full filled object.
	 * 
	 * @param reference
	 *            Specified reference.
	 * @throws XmlDataStoreDeleteException
	 *             This exception will be throw if an error arises in delete
	 *             process.
	 */
	public <T> void deleteAnnotatedObject(final T reference) throws XmlDataStoreDeleteException {
		if (reference == null)
			throw new XmlDataStoreRuntimeException("reference cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		final Class<?> cl = reference.getClass();
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				resource.deleteObject(reference, transaction);
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				resource.deleteObject(reference, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource referencesResource = resourcesManager.lockAnnotatedReferencesResource(cl, field, transaction);
			final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(reference, field, transaction);
			referencesResource.deleteReference(reference, transaction);
			resource.deleteObject(reference, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method deletes some objects.
	 * 
	 * @param references
	 *            Collection objects to remove.
	 * @throws XmlDataStoreDeleteException
	 *             This exception will throw if an error arises in delete
	 *             process.
	 */
	public <T> void deleteAnnotatedObjects(final Collection<T> references) throws XmlDataStoreDeleteException {
		if (references == null)
			throw new XmlDataStoreRuntimeException("references collection cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");
		final Class<?> cl = references.iterator().next().getClass();
		if (!checkHasAnnotatedObjectIdField(cl))
			throw new XmlDataStoreRuntimeException("the class " + cl + " must have annotated identifier field @XmlDataStoreObjectId");
		if (references.size() == 0)
			return;

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		final XmlDataStoreObjectIdField field = ObjectUtils.getAnnotatedObjectIdField(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			if (useFragmentation) {
				final XmlDataStoreAnnotatedIndexResource resource = resourcesManager.lockAnnotatedIndexResource(cl, field, transaction);
				for (final T reference : references) {
					resource.deleteObject(reference, transaction);
				}
			} else {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedClassResource(cl, field, transaction);
				for (final T reference : references) {
					resource.deleteObject(reference, transaction);
				}
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreAnnotatedResource referencesResource = resourcesManager.lockAnnotatedReferencesResource(cl, field, transaction);
			for (final T reference : references) {
				final XmlDataStoreAnnotatedResource resource = resourcesManager.lockAnnotatedObjectResource(reference, field, transaction);
				referencesResource.deleteReference(reference, transaction);
				resource.deleteObject(reference, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy " + XmlDataStorePolicy.ClassObjectsFile + " or "
			        + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	private boolean checkHasAnnotatedObjectIdField(final Class<?> cl) {
		return ObjectUtils.getAnnotatedObjectIdField(cl) != null;
	}
}
