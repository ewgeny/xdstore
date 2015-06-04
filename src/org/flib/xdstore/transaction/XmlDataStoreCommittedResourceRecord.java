package org.flib.xdstore.transaction;

import java.util.HashMap;
import java.util.Map;

class XmlDataStoreCommittedResourceRecord {

	private IXmlDataStoreResource     resource;

	private final Map<Object, Object> changes;

	public XmlDataStoreCommittedResourceRecord(final IXmlDataStoreResource resource) {
		this.resource = resource;
		this.changes = new HashMap<Object, Object>();
	}

	public IXmlDataStoreResource getResource() {
		return resource;
	}

	public void addChangeObject(final Object id, final Object object) {
		changes.put(id, object);
	}

	public void rollback(final XmlDataStoreTransaction transaction) {
		resource.rollbackFailedCommit(transaction, changes);
	}
}
