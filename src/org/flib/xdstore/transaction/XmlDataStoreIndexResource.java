package org.flib.xdstore.transaction;

import java.util.Collection;
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

	private XmlDataStoreIndexResourceCache                  index;

	private Map<String, Stack<IXmlDataStoreChangeRollback>> rollbacks = new ConcurrentHashMap<String, Stack<IXmlDataStoreChangeRollback>>();

	XmlDataStoreIndexResource(final XmlDataStoreResourcesManager manager, final XmlDataStoreTriggerManager triggersManager, final String resourceId,
	        final Map<Class<?>, XmlDataStorePolicy> policies, final IXmlDataStoreIOFactory factory, final int fragmentSize) {
		super(manager, triggersManager, resourceId, policies, factory);
		this.index = new XmlDataStoreIndexResourceCache(fragmentSize);
	}

	@Override
	void postPrepare(final XmlDataStoreTransaction transaction) {
		if (index.isClear()) {
			final Map<String, IXmlDataStoreIdentifiable> objects = super.readObjects(transaction);
			for (final IXmlDataStoreIdentifiable object : objects.values()) {
				final XmlDataStoreIndexRecord record = (XmlDataStoreIndexRecord) object;
				index.insertRecord(record.getDataStoreId(), record.getResourceId());
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
		final Collection<String> ids = index.getResourcesIds();
		for (final String resourceId : ids) {
			final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
			result.putAll(resource.readObjects(transaction));
		}
		return result;
	}

	public <T extends IXmlDataStoreIdentifiable> Map<String, T> readObjects(final XmlDataStoreTransaction transaction, final IXmlDataStorePredicate<T> predicate) {
		final Map<String, T> result = new HashMap<String, T>();
		final Collection<String> ids = index.getResourcesIds();
		for (final String resourceId : ids) {
			final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
			result.putAll(resource.readObjects(transaction, predicate));
		}
		return result;
	}

	public void readObjectByReference(final IXmlDataStoreIdentifiable reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		final String resourceId = index.getResourceId(reference.getDataStoreId());
		if (resourceId == null) {
			throw new XmlDataStoreReadException("cannot load by reference object of class " + reference.getClass() + " with id " + reference.getDataStoreId());
		}
		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		resource.readObjectByReference(reference, transaction);
	}

	public IXmlDataStoreIdentifiable readObject(final String id, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		final String resourceId = index.getResourceId(id);
		if (resourceId == null) {
			throw new XmlDataStoreReadException("cannot read object with id " + id);
		}
		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		return resource.readObject(id, transaction);
	}

	public void insertObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction) throws XmlDataStoreInsertException {
		final String objectId = object.getDataStoreId();
		final String resourceId = index.getResourceId(objectId);
		if (resourceId != null) {
			throw new XmlDataStoreInsertException("object of class " + object.getClass() + " with id " + objectId + " is exists");
		}
		boolean isInserted = false;
		final String freeResourceId = index.getFreeResourceId();
		if (freeResourceId != null) {
			final XmlDataStoreResource resource = manager.lockResource(freeResourceId, transaction);
			resource.insertObject(object, transaction);

			final XmlDataStoreIndexRecord record = new XmlDataStoreIndexRecord(objectId, resource.getResourceId());
			super.insertObject(record, transaction);

			index.insertRecord(objectId, resource.getResourceId());
			registerRollback(transaction, new IXmlDataStoreChangeRollback() {

				@Override
				public void rollback() {
					index.deleteRecord(objectId);
				}
			});
			isInserted = true;
		}
		if (!isInserted) {
			final XmlDataStoreResource resource = manager.lockResource(object.getClass(), objectId, transaction);
			resource.insertObject(object, transaction);

			final XmlDataStoreIndexRecord record = new XmlDataStoreIndexRecord(objectId, resource.getResourceId());
			super.insertObject(record, transaction);

			index.insertRecord(objectId, resource.getResourceId());
			registerRollback(transaction, new IXmlDataStoreChangeRollback() {

				@Override
				public void rollback() {
					index.deleteRecord(objectId);
				}
			});
		}
	}

	public void updateObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction) throws XmlDataStoreUpdateException {
		final String resourceId = index.getResourceId(object.getDataStoreId());
		if (resourceId == null) {
			throw new XmlDataStoreUpdateException("object of class " + object.getClass() + " with id " + object.getDataStoreId() + " does not exists");
		}
		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		resource.updateObject(object, transaction);
	}

	public void deleteObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction) throws XmlDataStoreDeleteException {
		final String objectId = object.getDataStoreId();
		final String resourceId = index.getResourceId(objectId);
		if (resourceId == null) {
			throw new XmlDataStoreDeleteException("object of class " + object.getClass() + " with id " + objectId + " does not exists");
		}

		final XmlDataStoreResource resource = manager.lockResource(resourceId, transaction);
		resource.deleteObject(object, transaction); // will be rolled back

		try {
			final XmlDataStoreIndexRecord record = (XmlDataStoreIndexRecord) super.readObject(objectId, transaction);
			super.deleteObject(record, transaction);
		} catch (final XmlDataStoreReadException e) {
			throw new XmlDataStoreDeleteException(e);
		}

		index.deleteRecord(objectId);
		registerRollback(transaction, new IXmlDataStoreChangeRollback() {

			@Override
			public void rollback() {
				index.insertRecord(objectId, resourceId);
			}
		});
	}
}
