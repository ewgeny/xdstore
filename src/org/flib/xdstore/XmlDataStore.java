package org.flib.xdstore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.flib.xdstore.serialization.IXmlDataStoreIOFactory;
import org.flib.xdstore.transaction.XmlDataStoreDeleteException;
import org.flib.xdstore.transaction.XmlDataStoreInsertException;
import org.flib.xdstore.transaction.XmlDataStoreReadException;
import org.flib.xdstore.transaction.XmlDataStoreResource;
import org.flib.xdstore.transaction.XmlDataStoreResourcesManager;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;
import org.flib.xdstore.transaction.XmlDataStoreTransactionsManager;
import org.flib.xdstore.transaction.XmlDataStoreUpdateException;
import org.flib.xdstore.utils.StringUtils;

/**
 * This class presents simple working interface. This one provides simple
 * methods for object persistence.
 * 
 * @author Евгений
 * 
 */
public class XmlDataStore {

	private final String                                                              folder;

	private final Map<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy> policies;

	private final XmlDataStoreTransactionsManager                                     transactionsManager;

	private final XmlDataStoreResourcesManager                                        resourcesManager;

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
		this.policies = new HashMap<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy>();
		this.transactionsManager = new XmlDataStoreTransactionsManager();
		this.resourcesManager = new XmlDataStoreResourcesManager(folder, this.policies);
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
	public void setStorePolicy(final Class<? extends IXmlDataStoreIdentifiable> cl, final XmlDataStorePolicy policy) {
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
	public XmlDataStorePolicy getStorePolicy(final Class<? extends IXmlDataStoreIdentifiable> cl) {
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
	 * This method saves root into store by store policy for the type (class) of
	 * the element.
	 * 
	 * @param root
	 *            Saved object
	 * @throws XmlDataStoreInsertException
	 *             This exception throws if in the save process arise error.
	 */
	public <T extends IXmlDataStoreIdentifiable> void saveRoot(final T root) throws XmlDataStoreInsertException {
		if (root == null)
			throw new XmlDataStoreRuntimeException("root cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = root.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			// resource with references to roots objects
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);
			// resource with root object
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(root, transaction);

			referencesResource.insertReference(root, transaction);
			resource.insertObject(root, transaction);
		} else if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			// resource with roots objects
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			resource.insertObject(root, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " has policy "
			        + XmlDataStorePolicy.ParentObjectFile);
		}
	}

	/**
	 * This method loads all root elements of specified class.
	 * 
	 * @param cl
	 *            Specified class
	 * @return Return map of the root elements.
	 * @throws XmlDataStoreReadException
	 *             This exception throws if in the load process arise error.
	 */
	@SuppressWarnings("unchecked")
	public <T extends IXmlDataStoreIdentifiable> Map<String, T> loadRoots(final Class<T> cl)
	        throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		Map<String, IXmlDataStoreIdentifiable> objects = null;
		XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			// resource with references to roots objects
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);

			objects = referencesResource.readReferences(transaction);
			for (final Map.Entry<String, IXmlDataStoreIdentifiable> entry : objects.entrySet()) {
				final XmlDataStoreResource resource = resourcesManager
				        .lockObjectResource(entry.getValue(), transaction);

				resource.readObjectByReference(entry.getValue(), transaction);
			}
		} else if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			// resource with roots objects
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			objects = resource.readObjects(transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " has policy "
			        + XmlDataStorePolicy.ParentObjectFile);
		}
		return (Map<String, T>) objects;
	}
	
	/**
	 * This method load roots by specified predicate. Can be used only for roots
	 * of class policy ClassObjectsFile and SingleObjectFile.
	 * 
	 * @param cl
	 *            Class of roots.
	 * @param predicate
	 *            Predicate for filtering roots.
	 * @return Return map of roots.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	@SuppressWarnings("unchecked")
	public <T extends IXmlDataStoreIdentifiable> Map<String, T> loadRoots(final Class<T> cl,
	        final IXmlDataStorePredicate<T> predicate) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (predicate == null)
			throw new XmlDataStoreRuntimeException("predicate cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
			return resource.readObjects(transaction, predicate);
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final Collection<T> result = new ArrayList<T>();
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);

			final Map<String, IXmlDataStoreIdentifiable> references = referencesResource.readReferences(transaction);
			for (final Map.Entry<String, IXmlDataStoreIdentifiable> entry : references.entrySet()) {
				final IXmlDataStoreIdentifiable reference = entry.getValue();

				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);
				resource.readObjectByReference(reference, transaction);
				if (predicate.passed((T) reference)) {
					result.add((T) reference);
				}
			}
			return (Map<String, T>) result;
		} else {
			throw new XmlDataStoreRuntimeException("root's class " + cl.getName() + " must have one policy "
			        + XmlDataStorePolicy.ClassObjectsFile + " or " + XmlDataStorePolicy.SingleObjectFile);
		}
	}

	/**
	 * This method load one root element by the identifier.
	 * 
	 * @param cl
	 *            Class of the required root element.
	 * @param id
	 *            Identifier of the required element.
	 * @return Return required root element if exist, else this method throws
	 *         exception.
	 * @throws XmlDataStoreReadException
	 *             This exception will be throw if required object isn't exist.
	 */
	@SuppressWarnings("unchecked")
	public <T extends IXmlDataStoreIdentifiable> T loadRoot(final Class<T> cl, final String id)
	        throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (StringUtils.isBlank(id))
			throw new XmlDataStoreRuntimeException("id cannot be blank");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		IXmlDataStoreIdentifiable object = null;
		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			// resource with references to roots objects
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);
			object = referencesResource.readReference(id, transaction);

			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(object, transaction);

			resource.readObjectByReference(object, transaction);
		} else if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			// resource with roots objects
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			object = resource.readObject(id, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " has policy "
			        + XmlDataStorePolicy.ParentObjectFile);
		}
		return (T) object;
	}

	/**
	 * This method updates specified root object.
	 * 
	 * @param root
	 *            Specified root object for update.
	 * @throws XmlDataStoreUpdateException
	 *             This exception will be throw if an error arises in update
	 *             operation.
	 */
	public <T extends IXmlDataStoreIdentifiable> void updateRoot(final T root) throws XmlDataStoreUpdateException {
		if (root == null)
			throw new XmlDataStoreRuntimeException("root cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = root.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			// resource with references to roots objects
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(root, transaction);

			resource.updateObject(root, transaction);
		} else if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			// resource with roots objects
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			resource.updateObject(root, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " has policy "
			        + XmlDataStorePolicy.ParentObjectFile);
		}
	}

	/**
	 * This method deletes specified root object.
	 * 
	 * @param root
	 *            Specified root object for delete.
	 * @throws XmlDataStoreUpdateException
	 *             This exception will be throw if an error arises in delete
	 *             operation.
	 */
	public <T extends IXmlDataStoreIdentifiable> void deleteRoot(final T root) throws XmlDataStoreReadException,
	        XmlDataStoreDeleteException {
		if (root == null)
			throw new XmlDataStoreRuntimeException("root cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = root.getClass();
		final String id = root.getId();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			// resource with references to roots objects
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);

			final IXmlDataStoreIdentifiable reference = referencesResource.readReference(id, transaction);

			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(root, transaction);

			referencesResource.deleteReference(reference, transaction);
			resource.deleteObject(root, transaction);
		} else if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			// resource with roots objects
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			resource.deleteObject(root, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " has policy "
			        + XmlDataStorePolicy.ParentObjectFile);
		}
	}

	/**
	 * This method deletes specified root object by identifier.
	 * 
	 * @param cl
	 *            Specified root class.
	 * @param id
	 *            Specified root identifier.
	 * @throws XmlDataStoreReadException
	 *             This exception will be throw if the root object isn't exist.
	 * @throws XmlDataStoreDeleteException
	 *             This exception will be throw if an error arises in delete
	 *             process.
	 */
	public <T extends IXmlDataStoreIdentifiable> void deleteRoot(final Class<T> cl, final String id)
	        throws XmlDataStoreReadException, XmlDataStoreDeleteException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (StringUtils.isBlank(id))
			throw new XmlDataStoreRuntimeException("id cannot be blank");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.SingleObjectFile) {
			// resource with references to roots objects
			final XmlDataStoreResource referencesResource = resourcesManager.lockReferencesResource(cl, transaction);

			final IXmlDataStoreIdentifiable reference = referencesResource.readReference(id, transaction);

			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);

			referencesResource.deleteReference(reference, transaction);
			resource.deleteObject(reference, transaction);
		} else if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			// resource with roots objects
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			final IXmlDataStoreIdentifiable object = resource.readObject(id, transaction);

			resource.deleteObject(object, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " has policy "
			        + XmlDataStorePolicy.ParentObjectFile);
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
	public <T extends IXmlDataStoreIdentifiable> void saveObject(final T object) throws XmlDataStoreInsertException {
		if (object == null)
			throw new XmlDataStoreRuntimeException("object cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = object.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			resource.insertObject(object, transaction);
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(object, transaction);

			resource.insertObject(object, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + object.getClass().getName()
			        + " has store policy " + XmlDataStorePolicy.ParentObjectFile);
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
	public <T extends IXmlDataStoreIdentifiable> void saveObjects(final Collection<T> objects)
	        throws XmlDataStoreInsertException {
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
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			for (final T object : objects) {
				resource.insertObject(object, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			for (final T object : objects) {
				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(object, transaction);

				resource.insertObject(object, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + cl.getName() + " has store policy "
			        + XmlDataStorePolicy.ParentObjectFile);
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
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			resource.readObjectByReference(reference, transaction);
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);

			resource.readObjectByReference(reference, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + reference.getClass().getName()
			        + " has store policy " + XmlDataStorePolicy.ParentObjectFile);
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
	public <T extends IXmlDataStoreIdentifiable> T loadObject(final Class<T> cl, final String id)
	        throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (StringUtils.isBlank(id))
			throw new XmlDataStoreRuntimeException("id cannot be blank");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			return (T) resource.readObject(id, transaction);
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(cl, id, transaction);

			return (T) resource.readObject(id, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + cl.getName() + " has store policy "
			        + XmlDataStorePolicy.ParentObjectFile);
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
	public <T extends IXmlDataStoreIdentifiable> boolean loadObjects(final Collection<T> references)
	        throws XmlDataStoreReadException {
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
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			for (final T reference : references) {
				resource.readObjectByReference(reference, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			for (final T reference : references) {
				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);

				resource.readObjectByReference(reference, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + cl.getName() + " has store policy "
			        + XmlDataStorePolicy.ParentObjectFile);
		}
		return result;
	}
	
	/**
	 * This method load objects by specified predicate. Can be used only for
	 * objects of class policy ClassObjectsFile.
	 * 
	 * @param cl
	 *            Class of objects.
	 * @param predicate
	 *            Predicate for filtering objects.
	 * @return Return map of objects.
	 * @throws XmlDataStoreReadException
	 *             This exception will throw if error arises in loading process.
	 */
	public <T extends IXmlDataStoreIdentifiable> Map<String, T> loadObjects(final Class<T> cl,
	        final IXmlDataStorePredicate<T> predicate) throws XmlDataStoreReadException {
		if (cl == null)
			throw new XmlDataStoreRuntimeException("class cannot be null");
		if (predicate == null)
			throw new XmlDataStoreRuntimeException("predicate cannot be null");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);
			return resource.readObjects(transaction, predicate);
		} else {
			throw new XmlDataStoreRuntimeException("class " + cl.getName() + " must have policy "
			        + XmlDataStorePolicy.ClassObjectsFile);
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
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			resource.updateObject(object, transaction);
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(object, transaction);

			resource.updateObject(object, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + object.getClass().getName()
			        + " has store policy " + XmlDataStorePolicy.ParentObjectFile);
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
	public <T extends IXmlDataStoreIdentifiable> void deleteObject(final T reference)
	        throws XmlDataStoreDeleteException {
		if (reference == null)
			throw new XmlDataStoreRuntimeException("reference cannot be null");

		if (!checkIsInTransaction())
			throw new XmlDataStoreRuntimeException("method must be execute in transaction");

		final XmlDataStoreTransaction transaction = transactionsManager.getTransaction();
		final Class<? extends IXmlDataStoreIdentifiable> cl = reference.getClass();
		final XmlDataStorePolicy policy = policies.get(cl);
		if (policy == XmlDataStorePolicy.ClassObjectsFile) {
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			resource.deleteObject(reference, transaction);
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);

			resource.deleteObject(reference, transaction);
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + reference.getClass().getName()
			        + " has store policy " + XmlDataStorePolicy.ParentObjectFile);
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
	public <T extends IXmlDataStoreIdentifiable> void deleteObjects(final Collection<T> references)
	        throws XmlDataStoreDeleteException {
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
			final XmlDataStoreResource resource = resourcesManager.lockClassResource(cl, transaction);

			for (final T reference : references) {
				resource.deleteObject(reference, transaction);
			}
		} else if (policy == XmlDataStorePolicy.SingleObjectFile) {
			for (final T reference : references) {
				final XmlDataStoreResource resource = resourcesManager.lockObjectResource(reference, transaction);

				resource.deleteObject(reference, transaction);
			}
		} else {
			throw new XmlDataStoreRuntimeException("object of class " + cl.getName() + " has store policy "
			        + XmlDataStorePolicy.ParentObjectFile);
		}
	}

}
