package org.flib.xdstore.operation;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdBlackHole;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class InsertGalaxyOperation implements Runnable {

	private final XmlDataStore store;

	public InsertGalaxyOperation(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		final Random rand = new Random();
		System.out.println("InsertGalaxyOperation Started");
		final XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			final Map<String, XdUniverse> universes = store.loadRoots(XdUniverse.class);
			if (universes.size() > 0) {
				int index = Math.abs(rand.nextInt()) % universes.size();
				Iterator<XdUniverse> it = universes.values().iterator();
				for (int i = 0; it.hasNext(); ++i) {
					final XdUniverse universe = it.next();
					if (i == index) {
						final XdGalaxy galaxy = generateGalaxy();
						universe.addGalaxy(galaxy);

						store.updateRoot(universe);
						store.saveObject(galaxy);
						break;
					}
				}
			}

			tx.commit();
			System.out.println("InsertGalaxyOperation Commited");
		} catch (final XmlDataStoreException e) {
			e.printStackTrace();
			tx.rollback();
			System.out.println("InsertGalaxyOperation Rolledback");
		}
	}

	private static XdGalaxy generateGalaxy() {
		final XdGalaxy galaxy = new XdGalaxy();
		galaxy.setId(nextStringId());
		galaxy.setSystems(new LinkedList<XdStarSystem>());

		final XdBlackHole hole = new XdBlackHole();
		hole.setId(nextStringId());

		galaxy.setHole(hole);

		return galaxy;
	}

	private static String nextStringId() {
		return UUID.randomUUID().toString();
	}
}
