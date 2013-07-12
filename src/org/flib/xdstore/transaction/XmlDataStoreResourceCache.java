package org.flib.xdstore.transaction;

import java.util.Collection;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.IXmlDataStorePredicate;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;
import org.flib.xdstore.utils.ObjectUtils;

public class XmlDataStoreResourceCache {
	
	private final XmlDataStoreResource					resource;

	private final Map<String, CacheRecord>              cache   = new TreeMap<String, CacheRecord>();

	private final Map<String, Map<String, CacheRecord>> changes = new TreeMap<String, Map<String, CacheRecord>>();
	
	XmlDataStoreResourceCache(final XmlDataStoreResource resource) {
		this.resource = resource;
	}

	public synchronized void fillCache(final Collection<IXmlDataStoreIdentifiable> objects) {
		for (final IXmlDataStoreIdentifiable object : objects) {
			cache.put(object.getDataStoreId(), CacheRecord.createReadRecord(object));
		}
	}

	public synchronized void clearCache() {
		cache.clear();
		changes.clear();
	}

	public synchronized Map<String, IXmlDataStoreIdentifiable> read(final XmlDataStoreTransaction transaction,
	        final IXmlDataStorePredicate<IXmlDataStoreIdentifiable> predicate) {
		final Map<String, IXmlDataStoreIdentifiable> result = new TreeMap<String, IXmlDataStoreIdentifiable>();
		for (final CacheRecord record : cache.values()) {

			IXmlDataStoreIdentifiable object = null;
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

			if (object != null && predicate.passed(object)) {
				result.put(object.getDataStoreId(), ObjectUtils.clone(object));
			}
		}
		return result;
	}

	public synchronized Map<String, IXmlDataStoreIdentifiable> read(final XmlDataStoreTransaction transaction) {
		final Map<String, IXmlDataStoreIdentifiable> result = new TreeMap<String, IXmlDataStoreIdentifiable>();
		for (final CacheRecord record : cache.values()) {

			IXmlDataStoreIdentifiable object = null;
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
				result.put(object.getDataStoreId(), ObjectUtils.clone(object));
			}
		}
		return result;
	}

	public synchronized IXmlDataStoreIdentifiable read(final String id, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreReadException {
		CacheRecord record = cache.get(id);
		if (record != null) {
			IXmlDataStoreIdentifiable object = null;
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

	public synchronized void readByReference(final IXmlDataStoreIdentifiable reference,
	        final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		CacheRecord record = cache.get(reference.getDataStoreId());
		if (record != null) {
			IXmlDataStoreIdentifiable object = null;
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
		throw new XmlDataStoreReadException("cannot load by reference object of class " + reference.getClass()
		        + " with id " + reference.getDataStoreId());
	}

	public synchronized void update(final IXmlDataStoreIdentifiable newObject, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreUpdateException {
		CacheRecord record = cache.get(newObject.getDataStoreId());
		if (record == null) {
			throw new XmlDataStoreUpdateException("object of class " + newObject.getClass() + " with id "
			        + newObject.getDataStoreId() + " does not exists");
		} else {
			if (record.isReadChange()) {
				record.lock(transaction);
				record.setNewObject(ObjectUtils.clone(newObject));
				record.markUpdate();
			} else if (!record.isCommitedState() && record.changedByTransaction(transaction)) {
				if (record.isUpdateChange() || record.isInsertChange()) {
					record.setNewObject(ObjectUtils.clone(newObject));
				} else {
					throw new XmlDataStoreUpdateException("object of class " + newObject.getClass() + " with id "
					        + newObject.getDataStoreId() + " was deleted this transaction");
				}
			} else if (record.canBeChangedByTransaction(transaction)) {
				record.lock(transaction);
				record.setNewObject(ObjectUtils.clone(newObject));
				record.markUpdate();
			} else {
				throw new XmlDataStoreUpdateException("concurrent modification one object of class "
				        + newObject.getClass() + " with id " + newObject.getDataStoreId());
			}
		}

		Map<String, CacheRecord> map = changes.get(transaction.getTransactionId());
		if (map == null) {
			changes.put(transaction.getTransactionId(), map = new HashMap<String, CacheRecord>());
		}
		if (!map.containsKey(newObject.getDataStoreId()))
			map.put(newObject.getDataStoreId(), record);
	}

	public synchronized void delete(final IXmlDataStoreIdentifiable oldObject, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreDeleteException {
		CacheRecord record = cache.get(oldObject.getDataStoreId());
		if (record == null) {
			throw new XmlDataStoreDeleteException("object of class " + oldObject.getClass() + " with id "
			        + oldObject.getDataStoreId() + " does not exists");
		} else {
			if (record.isReadChange()) {
				record.lock(transaction);
				record.setNewObject(null);
				record.markDelete();
			} else if (!record.isCommitedState() && record.changedByTransaction(transaction)) {
				if (record.isInsertChange()) {
					cache.remove(oldObject.getDataStoreId());
				} else if (record.isUpdateChange()) {
					record.setNewObject(null);
					record.markDelete();
				} else {
					throw new XmlDataStoreDeleteException("trying to double delete one object of class "
					        + oldObject.getClass() + " with id " + oldObject.getDataStoreId());
				}
			} else if (record.canBeChangedByTransaction(transaction)) {
				record.lock(transaction);
				record.setNewObject(null);
				record.markDelete();
			} else {
				throw new XmlDataStoreDeleteException("concurrent modification one object of class "
				        + oldObject.getClass() + " with id " + oldObject.getDataStoreId());
			}
		}

		Map<String, CacheRecord> map = changes.get(transaction.getTransactionId());
		if (map == null) {
			changes.put(transaction.getTransactionId(), map = new HashMap<String, CacheRecord>());
		}
		if (!map.containsKey(oldObject.getDataStoreId()))
			map.put(oldObject.getDataStoreId(), record);
	}

	public synchronized void insert(final IXmlDataStoreIdentifiable newObject, final XmlDataStoreTransaction transaction)
	        throws XmlDataStoreInsertException {
		CacheRecord record = cache.get(newObject.getDataStoreId());
		if (record == null) {
			cache.put(newObject.getDataStoreId(),
			        record = CacheRecord.createInsertRecord(ObjectUtils.clone(newObject), transaction));
		} else {
			if (record.changedByTransaction(transaction)) {
				if (record.isDeleteChange()) {
					throw new XmlDataStoreInsertException("trying to insert deleted object of class "
					        + newObject.getClass() + " with id " + newObject.getDataStoreId());
				} else if (record.isInsertChange()) {
					throw new XmlDataStoreInsertException("trying to double insert one object of class "
					        + newObject.getClass() + " with id " + newObject.getDataStoreId());
				} else {
					throw new XmlDataStoreInsertException("object of class " + newObject.getClass() + " with id "
					        + newObject.getDataStoreId() + " is exists");
				}
			} else {
				throw new XmlDataStoreInsertException("concurrent modification one object of class "
				        + newObject.getClass() + " with id " + newObject.getDataStoreId());
			}
		}
		Map<String, CacheRecord> map = changes.get(transaction.getTransactionId());
		if (map == null) {
			changes.put(transaction.getTransactionId(), map = new HashMap<String, CacheRecord>());
		}
		if (!map.containsKey(newObject.getDataStoreId()))
			map.put(newObject.getDataStoreId(), record);
	}

	public synchronized boolean hasChanges(final XmlDataStoreTransaction transaction) {
		return changes.containsKey(transaction.getTransactionId());
	}

	public synchronized void commit(final XmlDataStoreTransaction transaction) {
		final Map<String, CacheRecord> map = changes.remove(transaction.getTransactionId());
		if (map != null) {
			final Iterator<Entry<String, CacheRecord>> it = map.entrySet().iterator();
			while (it.hasNext()) {
				final CacheRecord record = it.next().getValue();
				IXmlDataStoreIdentifiable object = null;
				XmlDataStoreTriggerType type = null;
				if(record.isInsertChange()) {
					object = record.getNewObject();
					type = XmlDataStoreTriggerType.Insert;
				} else if(record.isUpdateChange()) {
					object = record.getNewObject();
					type = XmlDataStoreTriggerType.Update;
				} else if(record.isDeleteChange()) {
					object = record.getObject();
					type = XmlDataStoreTriggerType.Delete;
				}
				record.commit();
				if (record.getObject() == null)
					cache.remove(record.getId());
				record.unlock(transaction);

				if(type != null)
					resource.performTriggers(type, object);
			}
		}
	}

	public synchronized void rollback(final XmlDataStoreTransaction transaction) {
		final Map<String, CacheRecord> map = changes.remove(transaction.getTransactionId());
		if (map != null) {
			final Iterator<Entry<String, CacheRecord>> it = map.entrySet().iterator();
			while (it.hasNext()) {
				final CacheRecord record = it.next().getValue();
				record.rollback();
				if (record.getObject() == null)
					cache.remove(record.getId());
				record.unlock(transaction);
			}
		}
	}

	static class CacheRecord {

		static enum State {
			undefined, committed, rolledback
		};

		static enum Change {
			undefined, read, insert, update, delete
		};

		private String                    id;

		private IXmlDataStoreIdentifiable object;

		private IXmlDataStoreIdentifiable newObject;

		private XmlDataStoreTransaction   transaction;

		private long                      commitTimestamp = Long.MIN_VALUE;

		private Change                    change;

		private Change                    previousChange;

		private State                     state;

		private State                     previousState;

		private CacheRecord() {
			// do nothing
		}

		public static CacheRecord createReadRecord(final IXmlDataStoreIdentifiable object) {
			CacheRecord record = new CacheRecord();
			record.id = object.getDataStoreId();
			record.object = object;
			record.newObject = null;
			record.transaction = null;
			record.change = Change.read;
			record.state = State.undefined;
			return record;
		}

		public static CacheRecord createUpdateRecord(final IXmlDataStoreIdentifiable oldObject,
		        final IXmlDataStoreIdentifiable newObject, final XmlDataStoreTransaction transaction) {
			CacheRecord record = new CacheRecord();
			record.id = oldObject.getDataStoreId();
			record.object = oldObject;
			record.newObject = newObject;
			record.transaction = transaction;
			record.change = Change.update;
			record.state = State.undefined;
			return record;
		}

		public static CacheRecord createDeleteRecord(final IXmlDataStoreIdentifiable oldObject,
		        final XmlDataStoreTransaction transaction) {
			CacheRecord record = new CacheRecord();
			record.id = oldObject.getDataStoreId();
			record.object = oldObject;
			record.newObject = null;
			record.transaction = transaction;
			record.change = Change.delete;
			record.state = State.undefined;
			return record;
		}

		public static CacheRecord createInsertRecord(final IXmlDataStoreIdentifiable newObject,
		        final XmlDataStoreTransaction transaction) {
			CacheRecord record = new CacheRecord();
			record.id = newObject.getDataStoreId();
			record.object = null;
			record.newObject = newObject;
			record.transaction = transaction;
			record.change = Change.insert;
			record.state = State.undefined;
			return record;
		}

		public String getId() {
			return this.id;
		}

		public boolean canBeChangedByTransaction(final XmlDataStoreTransaction transaction) {
			return this.state == State.committed && transaction.getTimestart() > commitTimestamp;
		}

		public boolean changedByTransaction(final XmlDataStoreTransaction transaction) {
			return this.change != Change.read && this.transaction != null && this.transaction.isTransaction(transaction);
		}

		public IXmlDataStoreIdentifiable getObject() {
			return this.object;
		}

		public IXmlDataStoreIdentifiable getNewObject() {
			return this.newObject;
		}

		public void setNewObject(final IXmlDataStoreIdentifiable newObject) {
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
