package org.flib.xdstore.transaction;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.flib.xdstore.IXmlDataStoreAnnotatedPredicate;
import org.flib.xdstore.XmlDataStoreObjectIdField;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;
import org.flib.xdstore.utils.ObjectUtils;

public class XmlDataStoreAnnotatedResourceCache {

	private final XmlDataStoreObjectIdField             field;

	private final XmlDataStoreAnnotatedResource         resource;

	private final Map<Object, CacheRecord>              cache   = new TreeMap<Object, CacheRecord>();

	private final Map<String, Map<Object, CacheRecord>> changes = new TreeMap<String, Map<Object, CacheRecord>>();

	XmlDataStoreAnnotatedResourceCache(final XmlDataStoreAnnotatedResource resource, final XmlDataStoreObjectIdField field) {
		this.resource = resource;
		this.field = field;
	}

	public synchronized void fillCache(final Collection<Object> objects) {
		for (final Object object : objects) {
			cache.put(field.getObjectId(object), createReadRecord(object));
		}
	}

	@SuppressWarnings("unchecked")
	public synchronized <T> Map<Object, T> read(final XmlDataStoreTransaction transaction, final IXmlDataStoreAnnotatedPredicate<T> predicate) {
		final Map<Object, T> result = new TreeMap<Object, T>();
		for (final CacheRecord record : cache.values()) {

			Object object = null;
			if (record.isUpdateChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				} else {
					object = record.getObject();
				}
			} else if (record.isInsertChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				}
			} else if (record.isDeleteChange()) {
				if (!record.isCommitedState() && !record.changedByTransaction(transaction)) {
					object = record.getObject();
				}
			} else {
				object = record.getObject();
			}

			if (object != null && predicate.passed((T) object)) {
				result.put(field.getObjectId(object), (T) ObjectUtils.clone(object));
			}
		}
		return result;
	}

	public synchronized Map<Object, Object> read(final XmlDataStoreTransaction transaction) {
		final Map<Object, Object> result = new TreeMap<Object, Object>();
		for (final CacheRecord record : cache.values()) {

			Object object = null;
			if (record.isUpdateChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				} else {
					object = record.getObject();
				}
			} else if (record.isInsertChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				}
			} else if (record.isDeleteChange()) {
				if (!record.isCommitedState() && !record.changedByTransaction(transaction)) {
					object = record.getObject();
				}
			} else {
				object = record.getObject();
			}

			if (object != null) {
				result.put(field.getObjectId(object), ObjectUtils.clone(object));
			}
		}
		return result;
	}

	public synchronized Object read(final Object id, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		CacheRecord record = cache.get(id);
		if (record != null) {
			Object object = null;
			if (record.isUpdateChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				} else {
					object = record.getObject();
				}
			} else if (record.isInsertChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				}
			} else if (record.isDeleteChange()) {
				if (!record.isCommitedState() && !record.changedByTransaction(transaction)) {
					object = record.getObject();
				}
			} else {
				object = record.getObject();
			}

			if (object != null) {
				return ObjectUtils.clone(object);
			}
		}
		// ARCH ?
		throw new XmlDataStoreReadException("cannot read object with id " + record.getId());
	}

	public synchronized void readByReference(final Object reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		CacheRecord record = cache.get(field.getObjectId(reference));
		if (record != null) {
			Object object = null;
			if (record.isUpdateChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				} else {
					object = record.getObject();
				}
			} else if (record.isInsertChange()) {
				if (record.isCommitedState()) {
					object = record.getObject();
				} else if (record.changedByTransaction(transaction)) {
					object = record.getNewObject();
				}
			} else if (record.isDeleteChange()) {
				if (!record.isCommitedState() && !record.changedByTransaction(transaction)) {
					object = record.getObject();
				}
			} else {
				object = record.getObject();
			}

			if (object != null) {
				ObjectUtils.fillObject(reference, object);
				return;
			}
		}
		// ARCH ?
		throw new XmlDataStoreReadException("cannot load by reference object of class " + reference.getClass() + " with id " + field.getObjectId(reference));
	}

	public synchronized void update(final Object newObject, final XmlDataStoreTransaction transaction) throws XmlDataStoreUpdateException {
		final Object objectId = field.getObjectId(newObject);
		CacheRecord record = cache.get(objectId);
		if (record == null) {
			throw new XmlDataStoreUpdateException("object of class " + newObject.getClass() + " with id " + objectId + " does not exists");
		} else {
			if (record.isReadChange()) {
				record.lock(transaction);
				record.setNewObject(ObjectUtils.clone(newObject));
				record.markUpdate();
			} else if (!record.isCommitedState() && record.changedByTransaction(transaction)) {
				if (record.isUpdateChange() || record.isInsertChange()) {
					record.setNewObject(ObjectUtils.clone(newObject));
				} else {
					throw new XmlDataStoreUpdateException("object of class " + newObject.getClass() + " with id " + objectId + " was deleted this transaction");
				}
			} else if (record.canBeChangedByTransaction(transaction)) {
				record.lock(transaction);
				record.setNewObject(ObjectUtils.clone(newObject));
				record.markUpdate();
			} else {
				throw new XmlDataStoreUpdateException("concurrent modification one object of class " + newObject.getClass() + " with id " + objectId);
			}
		}

		Map<Object, CacheRecord> map = changes.get(transaction.getTransactionId());
		if (map == null) {
			changes.put(transaction.getTransactionId(), map = new HashMap<Object, CacheRecord>());
		}
		if (!map.containsKey(objectId))
			map.put(objectId, record);
	}

	public synchronized void delete(final Object oldObject, final XmlDataStoreTransaction transaction) throws XmlDataStoreDeleteException {
		final Object objectId = field.getObjectId(oldObject);
		CacheRecord record = cache.get(objectId);
		if (record == null) {
			throw new XmlDataStoreDeleteException("object of class " + oldObject.getClass() + " with id " + objectId + " does not exists");
		} else {
			if (record.isReadChange()) {
				record.lock(transaction);
				record.setNewObject(null);
				record.markDelete();
			} else if (!record.isCommitedState() && record.changedByTransaction(transaction)) {
				if (record.isInsertChange()) {
					cache.remove(objectId);
				} else if (record.isUpdateChange()) {
					record.setNewObject(null);
					record.markDelete();
				} else {
					throw new XmlDataStoreDeleteException("trying to double delete one object of class " + oldObject.getClass() + " with id " + objectId);
				}
			} else if (record.canBeChangedByTransaction(transaction)) {
				record.lock(transaction);
				record.setNewObject(null);
				record.markDelete();
			} else {
				throw new XmlDataStoreDeleteException("concurrent modification one object of class " + oldObject.getClass() + " with id " + objectId);
			}
		}

		Map<Object, CacheRecord> map = changes.get(transaction.getTransactionId());
		if (map == null) {
			changes.put(transaction.getTransactionId(), map = new HashMap<Object, CacheRecord>());
		}
		if (!map.containsKey(objectId))
			map.put(objectId, record);
	}

	public synchronized void insert(final Object newObject, final XmlDataStoreTransaction transaction) throws XmlDataStoreInsertException {
		final Object objectId = field.getObjectId(newObject);
		CacheRecord record = cache.get(objectId);
		if (record == null) {
			cache.put(objectId, record = createInsertRecord(ObjectUtils.clone(newObject), transaction));
		} else {
			if (record.changedByTransaction(transaction)) {
				if (record.isDeleteChange()) {
					throw new XmlDataStoreInsertException("trying to insert deleted object of class " + newObject.getClass() + " with id " + objectId);
				} else if (record.isInsertChange()) {
					throw new XmlDataStoreInsertException("trying to double insert one object of class " + newObject.getClass() + " with id " + objectId);
				} else {
					throw new XmlDataStoreInsertException("object of class " + newObject.getClass() + " with id " + objectId + " is exists");
				}
			} else {
				throw new XmlDataStoreInsertException("concurrent modification one object of class " + newObject.getClass() + " with id " + objectId);
			}
		}
		Map<Object, CacheRecord> map = changes.get(transaction.getTransactionId());
		if (map == null) {
			changes.put(transaction.getTransactionId(), map = new HashMap<Object, CacheRecord>());
		}
		if (!map.containsKey(objectId))
			map.put(objectId, record);
	}

	public synchronized boolean hasChanges(final XmlDataStoreTransaction transaction) {
		return changes.containsKey(transaction.getTransactionId());
	}

	public synchronized void commit(final XmlDataStoreTransaction transaction) {
		final Map<Object, CacheRecord> map = changes.remove(transaction.getTransactionId());
		if (map != null) {
			final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
			while (it.hasNext()) {
				final CacheRecord record = it.next().getValue();
				Object object = null;
				XmlDataStoreTriggerType type = null;
				if (record.isInsertChange()) {
					object = record.getNewObject();
					type = XmlDataStoreTriggerType.Insert;
				} else if (record.isUpdateChange()) {
					object = record.getNewObject();
					type = XmlDataStoreTriggerType.Update;
				} else if (record.isDeleteChange()) {
					object = record.getObject();
					type = XmlDataStoreTriggerType.Delete;
				}
				record.commit();
				if (record.getObject() == null)
					cache.remove(record.getId());
				record.unlock(transaction);

				if (type != null)
					resource.performTriggers(type, object);
			}
		}
	}

	public synchronized void rollback(final XmlDataStoreTransaction transaction) {
		final Map<Object, CacheRecord> map = changes.remove(transaction.getTransactionId());
		if (map != null) {
			final Iterator<Entry<Object, CacheRecord>> it = map.entrySet().iterator();
			while (it.hasNext()) {
				final CacheRecord record = it.next().getValue();
				record.rollback();
				if (record.getObject() == null)
					cache.remove(record.getId());
				record.unlock(transaction);
			}
		}
	}

	private CacheRecord createReadRecord(final Object object) {
		CacheRecord record = new CacheRecord();
		record.id = field.getObjectId(object);
		record.object = object;
		record.newObject = null;
		record.transaction = null;
		record.change = Change.read;
		record.state = State.undefined;
		return record;
	}

	private CacheRecord createInsertRecord(final Object newObject, final XmlDataStoreTransaction transaction) {
		CacheRecord record = new CacheRecord();
		record.id = field.getObjectId(newObject);
		record.object = null;
		record.newObject = newObject;
		record.transaction = transaction;
		record.change = Change.insert;
		record.state = State.undefined;
		return record;
	}

	private enum State {
		undefined, committed, rolledback
	};

	private enum Change {
		undefined, read, insert, update, delete
	};

	private class CacheRecord {

		private Object                  id;

		private Object                  object;

		private Object                  newObject;

		private XmlDataStoreTransaction transaction;

		private long                    commitTimestamp = Long.MIN_VALUE;

		private Change                  change;

		private Change                  previousChange;

		private State                   state;

		private State                   previousState;

		private CacheRecord() {
			// do nothing
		}

		public Object getId() {
			return this.id;
		}

		public boolean canBeChangedByTransaction(final XmlDataStoreTransaction transaction) {
			return this.state == State.committed && transaction.getTimestart() > commitTimestamp;
		}

		public boolean changedByTransaction(final XmlDataStoreTransaction transaction) {
			return this.change != Change.read && this.transaction != null && this.transaction.isTransaction(transaction);
		}

		public Object getObject() {
			return this.object;
		}

		public Object getNewObject() {
			return this.newObject;
		}

		public void setNewObject(final Object newObject) {
			this.newObject = newObject;
		}

		public boolean isCommitedState() {
			return this.state == State.committed;
		}

		public boolean isReadChange() {
			return this.change == Change.read;
		}

		public boolean isInsertChange() {
			return this.change == Change.insert;
		}

		public boolean isUpdateChange() {
			return this.change == Change.update;
		}

		public boolean isDeleteChange() {
			return this.change == Change.delete;
		}

		public void markUpdate() {
			this.previousChange = this.change;
			this.change = Change.update;
			this.previousState = this.state;
			this.state = State.undefined;
		}

		public void markDelete() {
			this.previousChange = this.change;
			this.change = Change.delete;
			this.previousState = this.state;
			this.state = State.undefined;
		}

		public void lock(final XmlDataStoreTransaction transaction) {
			this.transaction = transaction;
		}

		public void unlock(final XmlDataStoreTransaction transaction) {
			this.transaction = null;
		}

		public void commit() {
			this.commitTimestamp = System.currentTimeMillis();
			this.object = this.newObject;
			this.newObject = null;
			this.previousState = this.state;
			this.state = State.committed;
		}

		public void rollback() {
			this.newObject = null;
			this.state = previousState;
			this.previousState = State.undefined;
			this.change = previousChange;
			this.previousChange = Change.undefined;
		}

	}

}
