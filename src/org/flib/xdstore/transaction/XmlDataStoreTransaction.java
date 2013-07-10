package org.flib.xdstore.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeMap;
import java.util.Map;

public class XmlDataStoreTransaction {

	private final XmlDataStoreTransactionsManager   manager;

	private final String                            transactionId;

	private final long                              timestart;

	private final Map<String, XmlDataStoreResource> resources;

	XmlDataStoreTransaction(final XmlDataStoreTransactionsManager manager, final String transactionId) {
		this.manager = manager;
		this.transactionId = transactionId;
		this.timestart = System.currentTimeMillis();
		this.resources = new TreeMap<String, XmlDataStoreResource>();
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

	Collection<XmlDataStoreResource> getResources() {
		return new ArrayList<XmlDataStoreResource>(resources.values());
	}

	boolean isResourceRegistred(final XmlDataStoreResource resource) {
		return resources.containsKey(resource.getResourceId());
	}

	void registerResource(final XmlDataStoreResource resource) {
		if (!isResourceRegistred(resource)) {
			resources.put(resource.getResourceId(), resource);
			resource.prepare(this);
		}
	}

	void commitInternal() {
		for (final XmlDataStoreResource resource : resources.values()) {
			resource.commit(this);
		}
		resources.clear();
	}

	void rollbackInternal() {
		for (final XmlDataStoreResource resource : resources.values()) {
			resource.rollback(this);
		}
		resources.clear();
	}

	public boolean isTransaction(final XmlDataStoreTransaction transaction) {
		return transactionId.equals(transaction.getTransactionId());
	}
}
