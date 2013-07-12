package org.flib.xdstore.operation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdPlanet;
import org.flib.xdstore.entities.XdStar;
import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class InsertSystemOperation implements Runnable {

	private final XmlDataStore store;

	public InsertSystemOperation(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		final Random rand = new Random();
		System.out.println("InsertSystemOperation Started");
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

							final XdStarSystem system = generateSystem(Math.abs(rand.nextInt()) % 10);
							galaxy.addSystem(system);

							store.updateObject(galaxy);
							store.saveObject(system);
						}
						break;
					}
				}
			}

			tx.commit();
			System.out.println("InsertSystemOperation Commited");
		} catch (final XmlDataStoreException e) {
			e.printStackTrace();
			tx.rollback();
			System.out.println("InsertSystemOperation Rolledback");
		}
	}

	private static XdStarSystem generateSystem(int countPlanets) {
		final XdStarSystem system = new XdStarSystem();
		system.setDataStoreId(nextStringId());
		system.setStars(new LinkedList<XdStar>());
		system.setPlanets(new ArrayList<XdPlanet>());

		final XdStar star = new XdStar();
		star.setDataStoreId(nextStringId());

		system.addStar(star);

		for (int k = 0; k < countPlanets; ++k) {
			final XdPlanet planet = new XdPlanet();
			planet.setDataStoreId(nextStringId());

			system.addPlanet(planet);
		}

		return system;
	}

	private static String nextStringId() {
		return UUID.randomUUID().toString();
	}
}
