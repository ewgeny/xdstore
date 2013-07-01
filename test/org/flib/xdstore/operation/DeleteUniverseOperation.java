package org.flib.xdstore.operation;

import java.util.Collection;
import java.util.Map;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class DeleteUniverseOperation implements Runnable {

	private final XmlDataStore store;

	public DeleteUniverseOperation(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		System.out.println("DeleteUniverseOperation Started");
		final XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			final Map<String, XdUniverse> universes = store.loadRoots(XdUniverse.class);
			for (final XdUniverse uni : universes.values()) {

				final Collection<XdGalaxy> galaxies = uni.getGalaxies();
				store.loadObjects(galaxies);

				store.deleteRoot(uni);
				store.deleteObjects(galaxies);
				for (final XdGalaxy galaxy : galaxies) {
					store.deleteObjects(galaxy.getSystems()); // deleting by
					                                          // collection
					                                          // objects'
					                                          // references
				}

				break;
			}

			tx.commit();
			System.out.println("DeleteUniverseOperation Commited");
		} catch (final XmlDataStoreException e) {
			e.printStackTrace();
			tx.rollback();
			System.out.println("DeleteUniverseOperation Rolledback");
		}
	}

}
