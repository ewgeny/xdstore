package org.flib.xdstore.operation;

import java.util.Map;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class UpdateUniverseOperation implements Runnable {

	private final XmlDataStore store;

	public UpdateUniverseOperation(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		System.out.println("UpdateUniverseOperation Started");
		final XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			final Map<String, XdUniverse> universes = store.loadObjects(XdUniverse.class);
			for (final XdUniverse uni : universes.values()) {
				store.updateObject(uni);
				break;
			}

			tx.commit();
			System.out.println("UpdateUniverseOperation Commited");
		} catch (final XmlDataStoreException e) {
			e.printStackTrace();
			tx.rollback();
			System.out.println("UpdateUniverseOperation Rolledback");
		}
	}

}
