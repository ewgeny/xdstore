package org.flib.xdstore.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Map;

public class XmlDataStoreTransaction {

	private final XmlDataStoreTransactionsManager    manager;

	private final String                             transactionId;

	private final long                               timestart;

	private final Map<String, IXmlDataStoreResource> resources;

	XmlDataStoreTransaction(final XmlDataStoreTransactionsManager manager, final String transactionId) {
		this.manager = manager;
		this.transactionId = transactionId;
		this.timestart = System.currentTimeMillis();
		this.resources = new TreeMap<String, IXmlDataStoreResource>();
	}

	public String getTransactionId() {
		return transactionId;
	}

	public long getTimestart() {
		return timestart;
	}

	public void commit() {
		manager.commitTransaction(this);
	}

	public void rollback() {
		manager.rollbackTransaction(this);
	}

	Collection<IXmlDataStoreResource> getResources() {
		return new ArrayList<IXmlDataStoreResource>(resources.values());
	}

	boolean isResourceRegistred(final IXmlDataStoreResource resource) {
		return resources.containsKey(resource.getResourceId());
	}

	void registerResource(final IXmlDataStoreResource resource) {
		if (!isResourceRegistred(resource)) {
			resources.put(resource.getResourceId(), resource);
			resource.prepare(this);
		}
	}

	void commitInternal() {
		for (final IXmlDataStoreResource resource : resources.values()) {
			resource.commit(this);
		}
		resources.clear();
	}

	void rollbackInternal() {
		for (final IXmlDataStoreResource resource : resources.values()) {
			resource.rollback(this);
		}
		resources.clear();
	}

	public boolean isTransaction(final XmlDataStoreTransaction transaction) {
		return transactionId.equals(transaction.getTransactionId());
	}
}
