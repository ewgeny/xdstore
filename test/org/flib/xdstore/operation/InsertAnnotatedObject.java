package org.flib.xdstore.operation;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdAnnotatedObject;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class InsertAnnotatedObject implements Runnable {

	private final XmlDataStore store;

	private long               counter = 0;

	public InsertAnnotatedObject(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		System.out.println("InsertAnnotatedObject Started");
		final XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			store.saveAnnotatedObject(instanceNewObject());

			tx.commit();
			System.out.println("InsertAnnotatedObject Commited");
		} catch (final XmlDataStoreException e) {
			tx.rollback();
			System.out.println("InsertAnnotatedObject Rolledback");
		}
	}

	private XdAnnotatedObject instanceNewObject() {
		XdAnnotatedObject result = new XdAnnotatedObject();
		result.setObjectId(generateId());
		return result;
	}

	private synchronized Long generateId() {
		return counter++;
	}
}
