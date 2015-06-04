package org.flib.xdstore.transaction;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

import org.flib.xdstore.IXmlDataStoreAnnotatedPredicate;
import org.flib.xdstore.XmlDataStoreObjectIdField;
import org.flib.xdstore.XmlDataStorePolicy;
import org.flib.xdstore.XmlDataStoreRuntimeException;
import org.flib.xdstore.serialization.IXmlDataStoreIOFactory;
import org.flib.xdstore.serialization.IXmlDataStoreObjectsReader;
import org.flib.xdstore.serialization.IXmlDataStoreObjectsWriter;
import org.flib.xdstore.trigger.XmlDataStoreTriggerManager;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;

public class XmlDataStoreAnnotatedResource implements IXmlDataStoreResource {

	protected final XmlDataStoreResourcesManager     manager;

	private final XmlDataStoreTriggerManager         triggersManager;

	private final String                             resourceId;

	private final XmlDataStoreObjectIdField          field;

	private final boolean                            isReferences;

	private int                                      locks = 0;

	private final IXmlDataStoreObjectsReader         reader;

	private final IXmlDataStoreObjectsWriter         writer;

	private final XmlDataStoreAnnotatedResourceCache cache;

	XmlDataStoreAnnotatedResource(final XmlDataStoreResourcesManager manager, final XmlDataStoreTriggerManager triggersManager, final String resourceId,
	        final XmlDataStoreObjectIdField field, final Map<Class<?>, XmlDataStorePolicy> policies, final IXmlDataStoreIOFactory factory) {
		this(manager, triggersManager, resourceId, field, policies, false, factory);
	}

	XmlDataStoreAnnotatedResource(final XmlDataStoreResourcesManager manager, final XmlDataStoreTriggerManager triggersManager, final String resourceId,
	        final XmlDataStoreObjectIdField field, final Map<Class<?>, XmlDataStorePolicy> policies, final boolean isReferences, final IXmlDataStoreIOFactory factory) {
		this.manager = manager;
		this.triggersManager = triggersManager;
		this.resourceId = resourceId;
		this.field = field;
		this.isReferences = isReferences;
		this.reader = factory.newInstanceReader(policies);
		this.writer = factory.newInstanceWriter(policies);
		this.cache = new XmlDataStoreAnnotatedResourceCache(this, field);
	}

	public String getResourceId() {
		return resourceId;
	}

	public String getFileName() {
		return resourceId + ".xml";
	}

	void performTriggers(final XmlDataStoreTriggerType type, final Object object) {
		if (!isReferences) {
			triggersManager.performTriggers(type, object);
		}
	}

	public synchronized void prepare(final XmlDataStoreTransaction transaction) {
		++locks;
		if (locks == 1) {
			Collection<Object> objects = null;
			try {
				final File file = new File(getFileName());
				if (file.exists()) {
					Reader xmlReader = new FileReader(file);
					try {
						if (isReferences) {
							objects = reader.readAnnotatedReferences(xmlReader, field);
						} else {
							objects = reader.readAnnotatedObjects(xmlReader);
						}
					} finally {
						xmlReader.close();
					}
				}
			} catch (final Throwable e) {
				throw new XmlDataStoreRuntimeException("invalid state of database: error by reading resource " + resourceId, e);
			}
			if (objects != null)
				cache.fillCache(objects);
			postPrepare(transaction);
		}
	}

	void postPrepare(final XmlDataStoreTransaction transaction) {
		// do nothing
	}

	public synchronized void commit(final XmlDataStoreTransaction transaction, final XmlDataStoreCommittedResourceRecord record) {
		final boolean hasChanges = cache.hasChanges(transaction);

		Map<Object, Object> objects = null;
		if (hasChanges)
			objects = cache.read(transaction);

		cache.commit(transaction, record);
		if (hasChanges) {
			// TODO : REVIEW file deleting and creation
			// ! and think about backup file
			File file = new File(getFileName());
			if (file.exists())
				file.delete();

			if (objects.size() > 0) {
				try {
					// TODO : REVIEW file deleting and creation
					// ! and think about backup file
					file = new File(getFileName());
					if (!file.exists()) {
						final File parentFile = file.getParentFile();
						if (parentFile != null && !parentFile.exists())
							parentFile.mkdirs();
						file.createNewFile();
					}

					Writer xmlWriter = null;
					try {
						xmlWriter = new FileWriter(file);
						if (isReferences) {
							writer.writeAnnotatedReferences(xmlWriter, field, objects.values());
						} else {
							writer.writeAnnotatedObjects(xmlWriter, objects.values());
						}
					} finally {
						xmlWriter.close();
					}
				} catch (final Throwable e) {
					throw new XmlDataStoreRuntimeException("invalid state of database: error by writing resource " + resourceId, e);
				}
			}
		}
		--locks;
		postCommit(transaction);
	}

	void postCommit(final XmlDataStoreTransaction transaction) {
		// do nothing
	}

	public synchronized void rollback(final XmlDataStoreTransaction transaction) {
		cache.rollback(transaction);
		--locks;
		postRollback(transaction);
	}

	void postRollback(final XmlDataStoreTransaction transaction) {
		// do nothing
	}

	public synchronized void rollbackFailedCommit(final XmlDataStoreTransaction transaction, final Map<Object, Object> changes) {
		final boolean hasChanges = changes.size() > 0;

		cache.rollbackFailedCommit(transaction, changes);

		final Map<Object, Object> objects = cache.read(transaction);
		if (hasChanges) {
			// TODO : REVIEW file deleting and creation
			// ! and think about backup file
			File file = new File(getFileName());
			if (file.exists())
				file.delete();

			if (objects.size() > 0) {
				try {
					// TODO : REVIEW file deleting and creation
					// ! and think about backup file
					file = new File(getFileName());
					if (!file.exists()) {
						final File parentFile = file.getParentFile();
						if (parentFile != null && !parentFile.exists())
							parentFile.mkdirs();
						file.createNewFile();
					}

					Writer xmlWriter = null;
					try {
						xmlWriter = new FileWriter(file);
						if (isReferences) {
							writer.writeAnnotatedReferences(xmlWriter, field, objects.values());
						} else {
							writer.writeAnnotatedObjects(xmlWriter, objects.values());
						}
					} finally {
						xmlWriter.close();
					}
				} catch (final Throwable e) {
					throw new XmlDataStoreRuntimeException("invalid state of database: error by writing resource " + resourceId, e);
				}
			}
		}
		--locks;
		postRollbackFailedCommit(transaction);
	}

	void postRollbackFailedCommit(final XmlDataStoreTransaction transaction) {
		// do nothing
	}

	public void release() {
		manager.releaseResource(this);
	}

	public Map<Object, Object> readReferences(final XmlDataStoreTransaction transaction) {
		return cache.read(transaction);
	}

	public Object readReference(final String id, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		return cache.read(id, transaction);
	}

	public void insertReference(final Object reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreInsertException {
		cache.insert(reference, transaction);
	}

	public void deleteReference(final Object reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreDeleteException {
		cache.delete(reference, transaction);
	}
	
	public boolean hasObject(Object id, XmlDataStoreTransaction transaction) {
	    return cache.has(id, transaction);
    }

	public Map<Object, Object> readObjects(final XmlDataStoreTransaction transaction) {
		return cache.read(transaction);
	}

	public <T> Map<Object, T> readObjects(final XmlDataStoreTransaction transaction, final IXmlDataStoreAnnotatedPredicate<T> predicate) {
		return (Map<Object, T>) cache.read(transaction, (IXmlDataStoreAnnotatedPredicate<T>) predicate);
	}

	public void readObjectByReference(final Object reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		cache.readByReference(reference, transaction);
	}

	public Object readObject(final Object id, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		return cache.read(id, transaction);
	}

	public void insertObject(final Object object, final XmlDataStoreTransaction transaction) throws XmlDataStoreInsertException {
		cache.insert(object, transaction);
	}

	public void updateObject(final Object object, final XmlDataStoreTransaction transaction) throws XmlDataStoreUpdateException {
		cache.update(object, transaction);
	}

	public void deleteObject(final Object object, final XmlDataStoreTransaction transaction) throws XmlDataStoreDeleteException {
		cache.delete(object, transaction);
	}
}
