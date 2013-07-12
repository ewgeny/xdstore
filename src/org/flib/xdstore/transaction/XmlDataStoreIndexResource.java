package org.flib.xdstore.transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.IXmlDataStorePredicate;
import org.flib.xdstore.XmlDataStorePolicy;
import org.flib.xdstore.serialization.IXmlDataStoreIOFactory;
import org.flib.xdstore.trigger.XmlDataStoreTriggerManager;

public class XmlDataStoreIndexResource extends XmlDataStoreResource {

	private int                                             fragmentSize;

	/**
	 * objectId, resourceId
	 */
	private Map<String, String>                             index        = new ConcurrentHashMap<String, String>();

	private Map<String, Stack<IXmlDataStoreChangeRollback>> rollbacks    = new ConcurrentHashMap<String, Stack<IXmlDataStoreChangeRollback>>();

	XmlDataStoreIndexResource(final XmlDataStoreResourcesManager manager, final XmlDataStoreTriggerManager triggersManager,
			final String resourceId,
	        final Map<Class<? extends IXmlDataStoreIdentifiable>, XmlDataStorePolicy> policies,
	        final IXmlDataStoreIOFactory factory, final int fragmentSize) {
		super(manager, triggersManager, resourceId, policies, factory);
		this.fragmentSize = fragmentSize;
	}

	@Override
	void postPrepare(final XmlDataStoreTransaction transaction) {
		if (index.size() == 0) {
			final Map<String, IXmlDataStoreIdentifiable> indexObjects = super.readObjects(transaction);
			for (final IXmlDataStoreIdentifiable indexObject : indexObjects.values()) {
				final Map<String, IXmlDataStoreIdentifiable> objects = ((XmlDataStoreIndexObject) indexObject)
				        .getReferences();
				for (final IXmlDataStoreIdentifiable object : objects.values()) {
					index.put(object.getDataStoreId(), indexObject.getDataStoreId());
				}
			}
		}
	}

	@Override
	void postCommit(final XmlDataStoreTransaction transaction) {
		rollbacks.remove(transaction.getTransactionId());
	}

	@Override
	void postRollback(final XmlDataStoreTransaction transaction) {
		final Stack<IXmlDataStoreChangeRollback> stack = rollbacks.remove(transaction.getTransactionId());
		if (stack != null) {
			while (!stack.isEmpty()) {
				stack.pop().rollback();
			}
		}
	}

	private void registerRollback(final XmlDataStoreTransaction transaction, final IXmlDataStoreChangeRollback rb) {
		Stack<IXmlDataStoreChangeRollback> stack = rollbacks.get(transaction.getTransactionId());
		if (stack == null) {
			rollbacks.put(transaction.getTransactionId(), stack = new Stack<IXmlDataStoreChangeRollback>());
		}
		stack.push(rb);
	}

	public Map<String, IXmlDataStoreIdentifiable> readObjects(final XmlDataStoreTransaction transaction) {
		final Map<String, IXmlDataStoreIdentifiable> result = new HashMap<String, IXmlDataStoreIdentifiable>();
		final Map<String, IXmlDataStoreIdentifiable> indexObjects = super.readObjects(transaction);
		for (final IXmlDataStoreIdentifiable indexObject : indexObjects.values()) {
			final XmlDataStoreResource resource = manager.lockResource(indexObject.getDataStoreId(), transaction);
			result.putAll(resource.readObjects(transaction));
		}
		return result;
	}

	public <T extends IXmlDataStoreIdentifiable> Map<String, T> readObjects(final XmlDataStoreTransaction transaction,
	        final IXmlDataStorePredicate<T> predicate) {
		final Map<String, T> result = new HashMap<String, T>();
		final Map<String, IXmlDataStoreIdentifiable> indexObjects = super.readObjects(transaction);
		for (final IXmlDataStoreIdentifiable indexObject : indexObjects.values()) {
			final XmlDataStoreResource resource = manager.lockResource(indexObject.getDataStoreId(), transaction);
			result.putAll(resource.readObjects(transaction, predicate));
		}
		return result;
	}

	public void readObjectByReference(final IXmlDataStoreIdentifiable reference,
	        final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		final String resourceId = index.get(reference.getDataStoreId());
		if (resourceId == null) {
			throw new XmlDataStoreReadException("cannot load by reference object of class " + reference.getClass()
			        + " with id " + reference.getDataStoreId());
		}
		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		resource.readObjectByReference(reference, transaction);
	}

	public IXmlDataStoreIdentifiable readObject(final String id, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreReadException {
		final String resourceId = index.get(id);
		if (resourceId == null) {
			throw new XmlDataStoreReadException("cannot read object with id " + id);
		}
		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		return resource.readObject(id, transaction);
	}

	public void insertObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreInsertException {
		final String objectId = object.getDataStoreId();
		final String resourceId = index.get(objectId);
		if (resourceId != null) {
			throw new XmlDataStoreInsertException("object of class " + object.getClass() + " with id " + objectId
			        + " is exists");
		}
		boolean isInserted = false;
		final Map<String, IXmlDataStoreIdentifiable> indexObjects = super.readObjects(transaction);
		for (final IXmlDataStoreIdentifiable tmp : indexObjects.values()) {
			XmlDataStoreIndexObject indexObject = (XmlDataStoreIndexObject) tmp;
			if (indexObject.getCountReferences() < fragmentSize) {
				try {
					indexObject = (XmlDataStoreIndexObject) super.readObject(indexObject.getDataStoreId(), transaction);
				} catch (final XmlDataStoreReadException e) {
					throw new XmlDataStoreInsertException(e);
				}

				final XmlDataStoreResource resource = manager.lockResource(indexObject.getDataStoreId(), transaction);
				resource.insertObject(object, transaction); // will be
				                                            // rolled back
				index.put(objectId, indexObject.getDataStoreId());
				indexObject.addReference(object);
				try {
					super.updateObject(indexObject, transaction);
					registerRollback(transaction, new IXmlDataStoreChangeRollback() {

						@Override
						public void rollback() {
							index.remove(objectId);
						}
					});
				} catch (final XmlDataStoreUpdateException e) {
					index.remove(objectId);
					throw new XmlDataStoreInsertException(e);
				}
				isInserted = true;
				break;
			}
		}
		if (!isInserted) {
			final XmlDataStoreResource resource = manager.lockResource(object.getClass(), objectId, transaction);
			final XmlDataStoreIndexObject indexObject = new XmlDataStoreIndexObject();
			indexObject.setDataStoreId(resource.getResourceId());

			resource.insertObject(object, transaction); // will be rolled
			                                            // back
			index.put(objectId, indexObject.getDataStoreId());
			indexObject.addReference(object);
			try {
				super.insertObject(indexObject, transaction);
				registerRollback(transaction, new IXmlDataStoreChangeRollback() {

					@Override
					public void rollback() {
						index.remove(objectId);
					}
				});
			} catch (final XmlDataStoreInsertException e) {
				index.remove(objectId);
				throw e;
			}
		}
	}

	public void updateObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreUpdateException {
		final String resourceId = index.get(object.getDataStoreId());
		if (resourceId == null) {
			throw new XmlDataStoreUpdateException("object of class " + object.getClass() + " with id " + object.getDataStoreId()
			        + " does not exists");
		}
		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		resource.updateObject(object, transaction);
	}

	public void deleteObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreDeleteException {
		final String objectId = object.getDataStoreId();
		final String resourceId = index.get(objectId);
		if (resourceId == null) {
			throw new XmlDataStoreDeleteException("object of class " + object.getClass() + " with id " + objectId
			        + " does not exists");
		}
		final XmlDataStoreIndexObject indexObject;
		try {
			indexObject = (XmlDataStoreIndexObject) super.readObject(resourceId, transaction);
		} catch (final XmlDataStoreReadException e) {
			throw new XmlDataStoreDeleteException(e);
		}

		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		resource.deleteObject(object, transaction); // will be rolled back

		index.remove(objectId);
		indexObject.removeReference(object);
		try {
			if (indexObject.getCountReferences() == 0) {
				super.deleteObject(indexObject, transaction);
			} else {
				super.updateObject(indexObject, transaction);
			}
			registerRollback(transaction, new IXmlDataStoreChangeRollback() {

				@Override
				public void rollback() {
					index.put(objectId, resourceId);
				}
			});
		} catch (final XmlDataStoreDeleteException e) {
			index.put(objectId, resourceId);
			throw e;
		} catch (final XmlDataStoreUpdateException e) {
			index.put(objectId, resourceId);
			throw new XmlDataStoreDeleteException(e);
		}
	}
}
