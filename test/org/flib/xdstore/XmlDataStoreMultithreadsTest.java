package org.flib.xdstore;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

import org.flib.xdstore.entities.XdBlackHole;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdPlanet;
import org.flib.xdstore.entities.XdStar;
import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.operation.DeleteGalaxyOperation;
import org.flib.xdstore.operation.DeleteSystemOperation;
import org.flib.xdstore.operation.DeleteUniverseOperation;
import org.flib.xdstore.operation.InsertGalaxyOperation;
import org.flib.xdstore.operation.InsertSystemOperation;
import org.flib.xdstore.operation.InsertUniverseOperation;
import org.flib.xdstore.operation.UpdateGalaxyOperation;
import org.flib.xdstore.operation.UpdateSystemOperation;
import org.flib.xdstore.operation.UpdateUniverseOperation;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;

public class XmlDataStoreMultithreadsTest {

	public static void main(final String[] args) throws InterruptedException {
		final XmlDataStore store = initStore("./teststore");
		final Runnable[] operations = initOperations(store);

		for (int i = 0; i < 5; ++i) {
			XdUniverse universe = generateBigUniverse(10, 10, 10);
			// First initialization store
			XmlDataStoreTransaction tx = store.beginTransaction();
			try {
				store.saveRoot(universe);
				store.saveObjects(universe.getGalaxies());
				for (final XdGalaxy galaxy : universe.getGalaxies()) {
					store.saveObjects(galaxy.getSystems());
				}

				tx.commit();
			} catch (final XmlDataStoreException e) {
				e.printStackTrace();
				tx.rollback();
			}
		}

		// Running threads for test store
		final int countThreads = 1000;
		final int[] counter = new int[] { 0 };

		final Thread thread = new Thread(new Runnable() {

			@Override
			public void run() {
				synchronized (counter) {
					while (counter[0] != countThreads - 1)
						try {
							counter.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
				}
			}
		});

		thread.start();

		final Random rand = new Random(System.currentTimeMillis());
		for (int i = 0; i < countThreads; ++i) {
			final int opindex = rand.nextInt() % operations.length;
			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						operations[Math.abs(opindex)].run();
					} catch (final Exception e) {
						e.printStackTrace();
					}
					synchronized (counter) {
						++counter[0];
						counter.notifyAll();
					}
				}
			}).start();

			System.out.println(i);
			if (i % 20 == 0)
				Thread.sleep(250);
		}

		thread.join();
	}

	private static XmlDataStore initStore(final String storedir) {
		final XmlDataStore store = new XmlDataStore(storedir);
		store.setStorePolicy(XdUniverse.class, XmlDataStorePolicy.ClassObjectsFile);
		store.setStorePolicy(XdGalaxy.class, XmlDataStorePolicy.SingleObjectFile);
		store.setStorePolicy(XdBlackHole.class, XmlDataStorePolicy.ParentObjectFile);
		store.setStorePolicy(XdStarSystem.class, XmlDataStorePolicy.SingleObjectFile);
		store.setStorePolicy(XdStar.class, XmlDataStorePolicy.ParentObjectFile);
		store.setStorePolicy(XdPlanet.class, XmlDataStorePolicy.ParentObjectFile);
		return store;
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

	private static Runnable[] initOperations(final XmlDataStore store) {
		final ArrayList<Runnable> operations = new ArrayList<Runnable>();

		operations.add(new InsertUniverseOperation(store));
		operations.add(new UpdateUniverseOperation(store));
		operations.add(new DeleteUniverseOperation(store));

		operations.add(new InsertGalaxyOperation(store));
		operations.add(new UpdateGalaxyOperation(store));
		operations.add(new DeleteGalaxyOperation(store));

		operations.add(new InsertSystemOperation(store));
		operations.add(new UpdateSystemOperation(store));
		operations.add(new DeleteSystemOperation(store));
		//
		// operations.add(new InsertPlanetOperation(store));
		// operations.add(new UpdatePlanetOperation(store));
		// operations.add(new DeletePlanetOperation(store));

		return operations.toArray(new Runnable[operations.size()]);
	}
}
