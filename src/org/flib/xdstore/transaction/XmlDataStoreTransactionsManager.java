package org.flib.xdstore.transaction;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.flib.xdstore.XmlDataStoreRuntimeException;

public class XmlDataStoreTransactionsManager {

	private final Map<String, XmlDataStoreTransaction> transactions;

	public XmlDataStoreTransactionsManager() {
		transactions = new HashMap<String, XmlDataStoreTransaction>();
	}

	public synchronized XmlDataStoreTransaction beginTransaction() {
		final String transactionId = buildTransactionId();
		XmlDataStoreTransaction transaction = transactions.get(transactionId);
		if (transaction != null)
			throw new XmlDataStoreRuntimeException("transaction in this thread was started earlier");
		transactions.put(transactionId, transaction = new XmlDataStoreTransaction(this, transactionId));
		return transaction;
	}

	public void commitTransaction(final XmlDataStoreTransaction transaction) {
		final Collection<XmlDataStoreResource> resources;
		synchronized (this) {
			transactions.remove(transaction.getTransactionId());
			resources = transaction.getResources();
			transaction.commitInternal();
		}

		for (final XmlDataStoreResource resource : resources) {
			resource.release();
		}
	}

	public void rollbackTransaction(final XmlDataStoreTransaction transaction) {
		final Collection<XmlDataStoreResource> resources;
		synchronized (this) {
			transactions.remove(transaction.getTransactionId());
			resources = transaction.getResources();
			transaction.rollbackInternal();
		}

		for (final XmlDataStoreResource resource : resources) {
			resource.release();
		}
	}

	public synchronized XmlDataStoreTransaction getTransaction() {
		return transactions.get(buildTransactionId());
	}

	private String buildTransactionId() {
		final Thread thread = Thread.currentThread();
		return thread.getName() + thread.getId();
	}
}
