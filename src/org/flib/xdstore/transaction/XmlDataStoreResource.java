package org.flib.xdstore.transaction;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

import org.flib.xdstore.IXmlDataStoreIdentifiable;
import org.flib.xdstore.IXmlDataStorePredicate;
import org.flib.xdstore.XmlDataStorePolicy;
import org.flib.xdstore.XmlDataStoreRuntimeException;
import org.flib.xdstore.serialization.IXmlDataStoreIOFactory;
import org.flib.xdstore.serialization.IXmlDataStoreObjectsReader;
import org.flib.xdstore.serialization.IXmlDataStoreObjectsWriter;
import org.flib.xdstore.trigger.XmlDataStoreTriggerManager;
import org.flib.xdstore.trigger.XmlDataStoreTriggerType;

public class XmlDataStoreResource implements IXmlDataStoreResource {

	protected final XmlDataStoreResourcesManager manager;

	private final XmlDataStoreTriggerManager     triggersManager;

	private final String                         resourceId;

	private final boolean                        isReferences;

	private int                                  locks = 0;

	private final IXmlDataStoreObjectsReader     reader;

	private final IXmlDataStoreObjectsWriter     writer;

	private final XmlDataStoreResourceCache      cache;

	XmlDataStoreResource(final XmlDataStoreResourcesManager manager, final XmlDataStoreTriggerManager triggersManager, final String resourceId,
	        final Map<Class<?>, XmlDataStorePolicy> policies, final IXmlDataStoreIOFactory factory) {
		this(manager, triggersManager, resourceId, policies, false, factory);
	}

	XmlDataStoreResource(final XmlDataStoreResourcesManager manager, final XmlDataStoreTriggerManager triggersManager, final String resourceId,
	        final Map<Class<?>, XmlDataStorePolicy> policies, final boolean isReferences, final IXmlDataStoreIOFactory factory) {
		this.manager = manager;
		this.triggersManager = triggersManager;
		this.resourceId = resourceId;
		this.isReferences = isReferences;
		this.reader = factory.newInstanceReader(policies);
		this.writer = factory.newInstanceWriter(policies);
		this.cache = new XmlDataStoreResourceCache(this);
	}

	public String getResourceId() {
		return resourceId;
	}

	public String getFileName() {
		return resourceId + ".xml";
	}

	void performTriggers(final XmlDataStoreTriggerType type, final IXmlDataStoreIdentifiable object) {
		if (!isReferences) {
			triggersManager.performTriggers(type, object);
		}
	}

	public synchronized void prepare(final XmlDataStoreTransaction transaction) {
		++locks;
		if (locks == 1) {
			Collection<IXmlDataStoreIdentifiable> objects = null;
			try {
				final File file = new File(getFileName());
				if (file.exists()) {
					Reader xmlReader = new FileReader(file);
					try {
						if (isReferences) {
							objects = reader.readReferences(xmlReader);
						} else {
							objects = reader.readObjects(xmlReader);
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

	public synchronized void commit(final XmlDataStoreTransaction transaction, final XmlDatStoreCommittedResourceRecord record) {
		final boolean hasChanges = cache.hasChanges(transaction);

		Map<String, IXmlDataStoreIdentifiable> objects = null;
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
							writer.writeReferences(xmlWriter, objects.values());
						} else {
							writer.writeObjects(xmlWriter, objects.values());
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
		// TODO implement roll back failed commit
		final boolean hasChanges = changes.size() > 0;

		cache.rollbackFailedCommit(transaction, changes);

		Map<String, IXmlDataStoreIdentifiable> objects = cache.read(transaction);
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
							writer.writeReferences(xmlWriter, objects.values());
						} else {
							writer.writeObjects(xmlWriter, objects.values());
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

	public Map<String, IXmlDataStoreIdentifiable> readReferences(final XmlDataStoreTransaction transaction) {
		return cache.read(transaction);
	}

	public IXmlDataStoreIdentifiable readReference(final String id, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		return cache.read(id, transaction);
	}

	public void insertReference(final IXmlDataStoreIdentifiable reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreInsertException {
		cache.insert(reference, transaction);
	}

	public void deleteReference(final IXmlDataStoreIdentifiable reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreDeleteException {
		cache.delete(reference, transaction);
	}

	public Map<String, IXmlDataStoreIdentifiable> readObjects(final XmlDataStoreTransaction transaction) {
		return cache.read(transaction);
	}

	@SuppressWarnings("unchecked")
	public <T extends IXmlDataStoreIdentifiable> Map<String, T> readObjects(final XmlDataStoreTransaction transaction, final IXmlDataStorePredicate<T> predicate) {
		return (Map<String, T>) cache.read(transaction, (IXmlDataStorePredicate<IXmlDataStoreIdentifiable>) predicate);
	}

	public void readObjectByReference(final IXmlDataStoreIdentifiable reference, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		cache.readByReference(reference, transaction);
	}

	public IXmlDataStoreIdentifiable readObject(final String id, final XmlDataStoreTransaction transaction) throws XmlDataStoreReadException {
		return cache.read(id, transaction);
	}

	public void insertObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction) throws XmlDataStoreInsertException {
		cache.insert(object, transaction);
	}

	public void updateObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction) throws XmlDataStoreUpdateException {
		cache.update(object, transaction);
	}

	public void deleteObject(final IXmlDataStoreIdentifiable object, final XmlDataStoreTransaction transaction) throws XmlDataStoreDeleteException {
		cache.delete(object, transaction);
	}
}
