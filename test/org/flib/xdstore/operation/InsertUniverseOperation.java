package org.flib.xdstore.operation;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.UUID;

import org.flib.xdstore.XmlDataStore;
import org.flib.xdstore.XmlDataStoreException;
import org.flib.xdstore.entities.XdBlackHole;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdPlanet;
import org.flib.xdstore.entities.XdStar;
import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class InsertUniverseOperation implements Runnable {

	private final XmlDataStore store;

	public InsertUniverseOperation(final XmlDataStore store) {
		this.store = store;
	}

	@Override
	public void run() {
		final XdUniverse universe = generateBigUniverse(2, 3, 4);
		System.out.println("InsertUniverseOperation Started");
		final XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			store.saveRoot(universe);
			store.saveObjects(universe.getGalaxies());
			for (final XdGalaxy galaxy : universe.getGalaxies()) {
				store.saveObjects(galaxy.getSystems());
			}

			tx.commit();
			System.out.println("InsertUniverseOperation Commited");
		} catch (final XmlDataStoreException e) {
			e.printStackTrace();
			tx.rollback();
			System.out.println("InsertUniverseOperation Rolledback");
		}
	}

	private static XdUniverse generateBigUniverse(int countGalaxies, int countStarSystems, int countPlanets) {
		final XdUniverse universe = new XdUniverse();
		universe.setId(nextStringId());
		universe.setGalaxies(new ArrayList<XdGalaxy>());

		for (int i = 0; i < countGalaxies; ++i) {
			final XdGalaxy galaxy = new XdGalaxy();
			galaxy.setId(nextStringId());
			galaxy.setSystems(new LinkedList<XdStarSystem>());

			universe.addGalaxy(galaxy);

			final XdBlackHole hole = new XdBlackHole();
			hole.setId(nextStringId());

			galaxy.setHole(hole);

			for (int j = 0; j < countStarSystems; ++j) {
				final XdStarSystem system = new XdStarSystem();
				system.setId(nextStringId());
				system.setStars(new LinkedList<XdStar>());
				system.setPlanets(new ArrayList<XdPlanet>());

				galaxy.addSystem(system);

				final XdStar star = new XdStar();
				star.setId(nextStringId());

				system.addStar(star);

				for (int k = 0; k < countPlanets; ++k) {
					final XdPlanet planet = new XdPlanet();
					planet.setId(nextStringId());

					system.addPlanet(planet);
				}
			}
		}

		return universe;
	}

	private static String nextStringId() {
		return UUID.randomUUID().toString();
	}
}
