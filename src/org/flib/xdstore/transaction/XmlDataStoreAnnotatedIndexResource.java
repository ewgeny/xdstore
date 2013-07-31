package org.flib.xdstore.transaction;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import org.flib.xdstore.IXmlDataStoreAnnotatedPredicate;
import org.flib.xdstore.XmlDataStoreObjectId;
import org.flib.xdstore.XmlDataStoreObjectIdField;
import org.flib.xdstore.XmlDataStorePolicy;
import org.flib.xdstore.serialization.IXmlDataStoreIOFactory;
import org.flib.xdstore.trigger.XmlDataStoreTriggerManager;

public class XmlDataStoreAnnotatedIndexResource extends XmlDataStoreAnnotatedResource {

	private XmlDataStoreAnnotatedIndexResourceCache         index;

	private Map<String, Stack<IXmlDataStoreChangeRollback>> rollbacks = new ConcurrentHashMap<String, Stack<IXmlDataStoreChangeRollback>>();

	private final XmlDataStoreObjectIdField                 objectField;

	XmlDataStoreAnnotatedIndexResource(final XmlDataStoreResourcesManager manager, final XmlDataStoreTriggerManager triggersManager, final String resourceId,
	        final XmlDataStoreObjectIdField field, final Map<Class<?>, XmlDataStorePolicy> policies, final IXmlDataStoreIOFactory factory, final int fragmentSize) {
		super(manager, triggersManager, resourceId, getIndexRecordObjectIdField(), policies, factory);
		this.index = new XmlDataStoreAnnotatedIndexResourceCache(fragmentSize);
		this.objectField = field;
	}

	private static XmlDataStoreObjectIdField getIndexRecordObjectIdField() {
		final Class<?> cl = XmlDataStoreAnnotatedIndexRecord.class;
		final Field[] fields = cl.getDeclaredFields();
		for (final Field field : fields) {
			if (field.isAnnotationPresent(XmlDataStoreObjectId.class)) {
				return new XmlDataStoreObjectIdField(field);
			}
		}
		return null;
	}

	@Override
	void postPrepare(final XmlDataStoreTransaction transaction) {
		if (index.isClear()) {
			final Map<Object, Object> objects = super.readObjects(transaction);
			for (final Object object : objects.values()) {
				final XmlDataStoreAnnotatedIndexRecord record = (XmlDataStoreAnnotatedIndexRecord) object;
				index.insertRecord(record.getObjectId(), record.getResourceId());
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

	@Override
	void postRollbackFailedCommit(final XmlDataStoreTransaction transaction) {
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

	public Map<Object, Object> readObjects(final XmlDataStoreTransaction transaction) {
		final Map<Object, Object> result = new HashMap<Object, Object>();
		final Collection<String> ids = index.getResourcesIds();
		for (final String resourceId : ids) {
			final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(resourceId, objectField, transaction);
			result.putAll(resource.readObjects(transaction));
		}
		return result;
	}

	public <T> Map<Object, T> readObjects(final XmlDataStoreTransaction transaction, final IXmlDataStoreAnnotatedPredicate<T> predicate) {
		final Map<Object, T> result = new HashMap<Object, T>();
		final Collection<String> ids = index.getResourcesIds();
		for (final String resourceId : ids) {
			final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(resourceId, objectField, transaction);
			result.putAll(resource.readObjects(transaction, predicate));
		}
		return result;
	}

	public void readObjectByReference(final Object reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		final Object objectId = objectField.getObjectId(reference);
		final String resourceId = index.getResourceId(objectId);
		if (resourceId == null) {
			throw new XmlDataStoreReadException("cannot load by reference object of class " + reference.getClass() + " with id " + objectId);
		}
		final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(resourceId, objectField, transaction);
		resource.readObjectByReference(reference, transaction);
	}

	public Object readObject(final Object id, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		final String resourceId = index.getResourceId(id);
		if (resourceId == null) {
			throw new XmlDataStoreReadException("cannot read object with id " + id);
		}
		final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(resourceId, objectField, transaction);
		return resource.readObject(id, transaction);
	}

	public void insertObject(final Object object, final XmlDataStoreTransaction transaction) throws XmlDataStoreInsertException {
		final Object objectId = objectField.getObjectId(object);
		final String resourceId = index.getResourceId(objectId);
		if (resourceId != null) {
			throw new XmlDataStoreInsertException("object of class " + object.getClass() + " with id " + objectId + " is exists");
		}
		boolean isInserted = false;
		final String freeResourceId = index.getFreeResourceId();
		if (freeResourceId != null) {
			final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(freeResourceId, objectField, transaction);
			resource.insertObject(object, transaction);

			final XmlDataStoreAnnotatedIndexRecord record = new XmlDataStoreAnnotatedIndexRecord(objectId, resource.getResourceId());
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
			final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(object.getClass(), objectField, objectId, transaction);
			resource.insertObject(object, transaction);

			final XmlDataStoreAnnotatedIndexRecord record = new XmlDataStoreAnnotatedIndexRecord(objectId, resource.getResourceId());
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

	public void updateObject(final Object object, final XmlDataStoreTransaction transaction) throws XmlDataStoreUpdateException {
		final Object objectId = objectField.getObjectId(object);
		final String resourceId = index.getResourceId(objectId);
		if (resourceId == null) {
			throw new XmlDataStoreUpdateException("object of class " + object.getClass() + " with id " + objectId + " does not exists");
		}
		final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(resourceId, objectField, transaction);
		resource.updateObject(object, transaction);
	}

	public void deleteObject(final Object object, final XmlDataStoreTransaction transaction) throws XmlDataStoreDeleteException {
		final Object objectId = objectField.getObjectId(object);
		final String resourceId = index.getResourceId(objectId);
		if (resourceId == null) {
			throw new XmlDataStoreDeleteException("object of class " + object.getClass() + " with id " + objectId + " does not exists");
		}

		final XmlDataStoreAnnotatedResource resource = manager.lockAnnotatedResource(resourceId, objectField, transaction);
		resource.deleteObject(object, transaction); // will be rolled back

		try {
			final XmlDataStoreAnnotatedIndexRecord record = (XmlDataStoreAnnotatedIndexRecord) super.readObject(objectId, transaction);
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
