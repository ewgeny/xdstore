package org.flib.xdstore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import org.flib.xdstore.entities.XdBlackHole;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdPlanet;
import org.flib.xdstore.entities.XdStar;
import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class XmlDataStoreTest {

	public static void main(final String[] args) {
		// for(int i = 0; i < 10; ++i) {
		// testWriteToStore("./teststore");
		// System.out.println(i);
		// }

		// testReadFromStore("./teststore");
		testDeleteFromStore("./teststore");
	}

	private static XmlDataStore initStore(final String storedir) {
		final XmlDataStore store = new XmlDataStore(storedir);
		store.setStorePolicy(XdUniverse.class, XmlDataStorePolicy.ClassObjectsFile);
		store.setStorePolicy(XdGalaxy.class, XmlDataStorePolicy.ClassObjectsFile);
		store.setStorePolicy(XdBlackHole.class, XmlDataStorePolicy.ParentObjectFile);
		store.setStorePolicy(XdStarSystem.class, XmlDataStorePolicy.SingleObjectFile);
		store.setStorePolicy(XdStar.class, XmlDataStorePolicy.ParentObjectFile);
		store.setStorePolicy(XdPlanet.class, XmlDataStorePolicy.ParentObjectFile);
		return store;
	}

	private static void testWriteToStore(final String storedir) {
		final XmlDataStore store = initStore(storedir);

		final XdUniverse universe = generateBigUniverse(10, 10, 10);
		XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			store.saveRoot(universe);
			store.saveObjects(universe.getGalaxies());
			for (final XdGalaxy galaxy : universe.getGalaxies()) {
				store.saveObjects(galaxy.getSystems());
			}

			tx.commit();
		} catch (XmlDataStoreException e) {
			e.printStackTrace();
			if (tx != null)
				tx.rollback();
		}
	}

	private static void testReadFromStore(final String storedir) {
		final XmlDataStore store = initStore(storedir);

		XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			int i = 0;
			final Map<String, XdUniverse> roots = store.loadRoots(XdUniverse.class);
			for (final XdUniverse root : roots.values()) {
				final Collection<XdGalaxy> galaxies = root.getGalaxies();
				store.loadObjects(galaxies);

				for (final XdGalaxy galaxy : galaxies) {
					final Collection<XdStarSystem> systems = galaxy.getSystems();
					store.loadObjects(systems);
				}
				System.out.println(i);
				++i;
			}

			tx.commit();
		} catch (XmlDataStoreException e) {
			e.printStackTrace();
			if (tx != null)
				tx.rollback();
		}
	}

	private static void testDeleteFromStore(final String storedir) {
		final XmlDataStore store = initStore(storedir);

		XmlDataStoreTransaction tx = store.beginTransaction();
		try {
			int i = 0;
			final Map<String, XdUniverse> roots = store.loadRoots(XdUniverse.class);
			for (final XdUniverse root : roots.values()) {
				final Collection<XdGalaxy> galaxies = root.getGalaxies();
				store.loadObjects(galaxies);

				for (final XdGalaxy galaxy : galaxies) {
					store.deleteObjects(galaxy.getSystems());
				}
				store.deleteObjects(galaxies);
				store.deleteRoot(root);

				System.out.println(i);
				++i;
			}

			tx.commit();
		} catch (XmlDataStoreException e) {
			e.printStackTrace();
			if (tx != null)
				tx.rollback();
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
