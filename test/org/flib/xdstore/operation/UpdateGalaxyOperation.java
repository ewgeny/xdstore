package org.flib.xdstore.operation;

import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class UpdateGalaxyOperation implements Runnable {

	private final XmlDataStore store;

	public UpdateGalaxyOperation(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		final Random rand = new Random();
		System.out.println("UpdateGalaxyOperation Started");
		final XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			final Map<String, XdUniverse> universes = store.loadObjects(XdUniverse.class);
			if (universes.size() > 0) {
				int index = Math.abs(rand.nextInt()) % universes.size();
				Iterator<XdUniverse> it = universes.values().iterator();
				for (int i = 0; it.hasNext(); ++i) {
					final XdUniverse universe = it.next();
					if (i == index) {
						if (universe.getGalaxies().size() > 0) {
							index = Math.abs(rand.nextInt()) % universe.getGalaxies().size();
							final XdGalaxy galaxy = universe.getGalaxy(index);

							store.loadObject(galaxy);
							store.updateObject(galaxy);
							if (galaxy.getObject() != null)
								store.updateAnnotatedObject(galaxy.getObject());
						}
						break;
					}
				}
			}

			tx.commit();
			System.out.println("UpdateGalaxyOperation Commited");
		} catch (final XmlDataStoreException e) {
			e.printStackTrace();
			tx.rollback();
			System.out.println("UpdateGalaxyOperation Rolledback");
		}
	}

}
