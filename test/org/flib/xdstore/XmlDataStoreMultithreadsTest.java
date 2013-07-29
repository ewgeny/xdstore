package org.flib.xdstore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Random;
import java.util.UUID;

import org.flib.xdstore.entities.XdAnnotatedObject;
import org.flib.xdstore.entities.XdBlackHole;
import org.flib.xdstore.entities.XdGalaxy;
import org.flib.xdstore.entities.XdPlanet;
import org.flib.xdstore.entities.XdStar;
import org.flib.xdstore.entities.XdStarSystem;
import org.flib.xdstore.entities.XdUniverse;
import org.flib.xdstore.operation.DeleteAnnotatedObject;
import org.flib.xdstore.operation.DeleteGalaxyOperation;
import org.flib.xdstore.operation.DeleteSystemOperation;
import org.flib.xdstore.operation.DeleteUniverseOperation;
import org.flib.xdstore.operation.InsertAnnotatedObject;
import org.flib.xdstore.operation.InsertGalaxyOperation;
import org.flib.xdstore.operation.InsertSystemOperation;
import org.flib.xdstore.operation.InsertUniverseOperation;
import org.flib.xdstore.operation.UpdateAnnotatedObject;
import org.flib.xdstore.operation.UpdateGalaxyOperation;
import org.flib.xdstore.operation.UpdateSystemOperation;
import org.flib.xdstore.operation.UpdateUniverseOperation;
import org.flib.xdstore.transaction.XmlDataStoreTransaction;
import org.flib.xdstore.triggers.XdGalaxyDeleteTrigger;
import org.flib.xdstore.triggers.XdGalaxyInsertTrigger;
import org.flib.xdstore.triggers.XdGalaxyUpdateTrigger;
import org.flib.xdstore.triggers.XdStarSystemDeleteTrigger;
import org.flib.xdstore.triggers.XdStarSystemInsertTrigger;
import org.flib.xdstore.triggers.XdStarSystemUpdateTrigger;
import org.flib.xdstore.triggers.XdUniverseDeleteTrigger;
import org.flib.xdstore.triggers.XdUniverseInsertTrigger;
import org.flib.xdstore.triggers.XdUniverseUpdateTrigger;

public class XmlDataStoreMultithreadsTest {

	public static void main(final String[] args) throws InterruptedException {
		final XmlDataStore store = initStore("./teststore");
		final Runnable[] operations = initOperations(store);

		for (int i = 0; i < 5; ++i) {
			XdUniverse universe = generateBigUniverse(10, 10, 10);
			// First initialization store
			XmlDataStoreTransaction tx = store.beginTransaction();
			try {
				store.saveObject(universe);
				store.saveObjects(universe.getGalaxies());
				for (final XdGalaxy galaxy : universe.getGalaxies()) {
					store.saveObjects(galaxy.getSystems());
				}
				
//				store.saveAnnotatedObjects(generateAnnotatedObjects(50));

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
		final XmlDataStore store = new XmlDataStore(storedir, 20);
		store.setStorePolicy(XdUniverse.class, XmlDataStorePolicy.ClassObjectsFile);
		store.setStorePolicy(XdGalaxy.class, XmlDataStorePolicy.ClassObjectsFile);
		store.setStorePolicy(XdBlackHole.class, XmlDataStorePolicy.ParentObjectFile);
		store.setStorePolicy(XdStarSystem.class, XmlDataStorePolicy.SingleObjectFile);
		store.setStorePolicy(XdStar.class, XmlDataStorePolicy.ParentObjectFile);
		store.setStorePolicy(XdPlanet.class, XmlDataStorePolicy.ParentObjectFile);
		store.setStorePolicy(XdAnnotatedObject.class, XmlDataStorePolicy.SingleObjectFile);

		store.registerTrigger(new XdUniverseInsertTrigger());
		store.registerTrigger(new XdUniverseUpdateTrigger());
		store.registerTrigger(new XdUniverseDeleteTrigger());

		store.registerTrigger(new XdGalaxyInsertTrigger());
		store.registerTrigger(new XdGalaxyUpdateTrigger());
		store.registerTrigger(new XdGalaxyDeleteTrigger());

		store.registerTrigger(new XdStarSystemInsertTrigger());
		store.registerTrigger(new XdStarSystemUpdateTrigger());
		store.registerTrigger(new XdStarSystemDeleteTrigger());
		return store;
	}

	private static XdUniverse generateBigUniverse(int countGalaxies, int countStarSystems, int countPlanets) {
		final XdUniverse universe = new XdUniverse();
		universe.setDataStoreId(nextStringId());
		universe.setGalaxies(new ArrayList<XdGalaxy>());

		for (int i = 0; i < countGalaxies; ++i) {
			final XdGalaxy galaxy = new XdGalaxy();
			galaxy.setDataStoreId(nextStringId());
			galaxy.setSystems(new LinkedList<XdStarSystem>());

			universe.addGalaxy(galaxy);

			final XdBlackHole hole = new XdBlackHole();
			hole.setDataStoreId(nextStringId());

			galaxy.setHole(hole);

			for (int j = 0; j < countStarSystems; ++j) {
				final XdStarSystem system = new XdStarSystem();
				system.setDataStoreId(nextStringId());
				system.setStars(new LinkedList<XdStar>());
				system.setPlanets(new ArrayList<XdPlanet>());

				galaxy.addSystem(system);

				final XdStar star = new XdStar();
				star.setDataStoreId(nextStringId());

				system.addStar(star);

				for (int k = 0; k < countPlanets; ++k) {
					final XdPlanet planet = new XdPlanet();
					planet.setDataStoreId(nextStringId());

					system.addPlanet(planet);
				}
			}
		}

		return universe;
	}
	
	private static Collection<XdAnnotatedObject> generateAnnotatedObjects(int count) {
		final Random rand = new Random();
		final Collection<XdAnnotatedObject> result = new ArrayList<XdAnnotatedObject>(count);
		
		for(int i = 0; i < count; ++i) {
			final XdAnnotatedObject object = new XdAnnotatedObject();
			object.setObjectId(Math.abs(rand.nextLong()));
			result.add(object);
		}
		return result;
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
		
		// operations.add(new InsertPlanetOperation(store));
		// operations.add(new UpdatePlanetOperation(store));
		// operations.add(new DeletePlanetOperation(store));
		
//		operations.add(new InsertAnnotatedObject(store));
//		operations.add(new UpdateAnnotatedObject(store));
//		operations.add(new DeleteAnnotatedObject(store));

		return operations.toArray(new Runnable[operations.size()]);
	}
}
