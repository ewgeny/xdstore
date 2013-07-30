package org.flib.xdstore.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.flib.xdstore.XmlDataStoreRuntimeException;

public class XmlDataStoreTransactionsManager {

	private final Map<String, XmlDataStoreTransaction> transactions;

	private boolean                                    invalidState = false;

	public XmlDataStoreTransactionsManager() {
		transactions = new HashMap<String, XmlDataStoreTransaction>();
	}

	public synchronized XmlDataStoreTransaction beginTransaction() {
		if (invalidState)
			throw new XmlDataStoreRuntimeException("Invalid state of database. Please wait to start new transaction...");

		final String transactionId = buildTransactionId();
		XmlDataStoreTransaction transaction = transactions.get(transactionId);
		if (transaction != null)
			throw new XmlDataStoreRuntimeException("transaction in this thread was started earlier");
		transactions.put(transactionId, transaction = new XmlDataStoreTransaction(this, transactionId));
		return transaction;
	}

	public void commitTransaction(final XmlDataStoreTransaction transaction) {
		final Collection<IXmlDataStoreResource> resources;
		synchronized (this) {
			transactions.remove(transaction.getTransactionId());
			resources = transaction.getResources();
			if (invalidState) {
				transaction.rollbackInternal(); // roll back active transaction
												// by invalid state, and at the
												// end of this method will throw
												// exception
			} else {
				final Collection<XmlDatStoreCommittedResourceRecord> committedResources = new LinkedList<XmlDatStoreCommittedResourceRecord>();
				try {
					transaction.commitInternal(committedResources);
				} catch (final XmlDataStoreRuntimeException e) {
					e.printStackTrace();
					invalidState = true;
					while (transactions.size() > 0)
						try {
							wait(); // waiting for a stop all active
									// transactions
						} catch (final InterruptedException e1) {
							e1.printStackTrace();
						}
					// roll back resources with committed changes
					final Collection<IXmlDataStoreResource> notChangedResources = new ArrayList<IXmlDataStoreResource>(resources);
					for (final XmlDatStoreCommittedResourceRecord committed : committedResources) {
						committed.rollback(transaction);
						final Iterator<IXmlDataStoreResource> it = notChangedResources.iterator();
						while (it.hasNext()) {
							if (it.next() == committed.getResource()) {
								it.remove();
								break;
							}
						}
					}
					// roll back resources with uncommitted changes
					for (final IXmlDataStoreResource notChanged : notChangedResources) {
						notChanged.rollback(transaction);
					}
					// clear flag invalid state of data base
					invalidState = false;
				}
			}
		}

		for (final IXmlDataStoreResource resource : resources) {
			resource.release();
		}

		synchronized (this) {
			if (invalidState) {
				if (transactions.size() == 0)
					notify();
				throw new XmlDataStoreRuntimeException("transaction rolled back by invalid data base's state");
			}
		}
	}

	public void rollbackTransaction(final XmlDataStoreTransaction transaction) {
		final Collection<IXmlDataStoreResource> resources;
		synchronized (this) {
			transactions.remove(transaction.getTransactionId());
			resources = transaction.getResources();
			transaction.rollbackInternal();
		}

		for (final IXmlDataStoreResource resource : resources) {
			resource.release();
		}

		synchronized (this) {
			if (invalidState && transactions.size() == 0)
				notify();
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
