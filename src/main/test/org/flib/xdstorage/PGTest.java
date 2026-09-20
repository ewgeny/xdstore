package org.flib.xdstorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.entities.*;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.operation.*;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@FixMethodOrder(MethodSorters.JVM)
@Ignore
public class PGTest {

    private static final Logger log = LogManager.getLogger(PGTest.class);

    private static void deleteAllUniverses(IXdStorage storage) {
        IXdStorageTransaction tx = storage.beginTransaction();
        try {
            log.info("CLEARING STORE STARTED");

            Collection<XdUniverse> universes = storage.load(XdUniverse.class);
            for (XdUniverse universe : universes) {
                storage.load(universe);

                Collection<XdGalaxy> galaxies = universe.getGalaxies();
                storage.load(galaxies);

                for (XdGalaxy galaxy : galaxies) {
//                    if (galaxy.getObject() != null) {
//                        storage.delete(galaxy.getObject());
//                    }

                    Collection<XdStarSystem> systems = galaxy.getSystems();
                    if(systems != null) {
                        for (XdStarSystem system : systems) {
                            storage.load(system);

                            if (system.getPlanets() != null) {
                                storage.delete(system.getPlanets());
                            }

                            storage.delete(system);
                        }
                    }

                    storage.delete(galaxy);
                }
            }
            storage.delete(universes);

            tx.commit();
            log.info("CLEARING STORE SUCCESS");
        } catch (XdStorageException | XdStorageConnectionException e) {
            tx.rollback();
            log.info("CLEARING STORE FAILED");
            log.info("error", e);
        }
    }

    private static XdPlanet generatePlanet() {
        final XdPlanet planet = new XdPlanet();
        planet.setDate(new Date());
        planet.setWaterPercent(30);
        planet.setName("My planet");
        planet.setStrId(UUID.randomUUID().toString());

        XdInternalObject internalObject = new XdInternalObject();
        internalObject.setName("Internal Object");
        internalObject.setValue(1);

        List<XdInternalObject> internalObjects = new ArrayList<>();
        internalObjects.add(internalObject);
        planet.setInternalObjects(internalObjects);

        generateSatellites(planet, 1);

        return planet;
    }

    private static void generateSatellites(final XdPlanet planet, final int i) {
        final XdSatellite satellite = new XdSatellite();
        satellite.setName("ColSatellite " + i);
//        satellite.setId(UUID.randomUUID().toString());
        planet.addSatellite(satellite);

        final XdSatellite satellite2 = new XdSatellite();
        satellite2.setName("MapSatellite " + i);
//        satellite2.setId(UUID.randomUUID().toString());
        planet.addSatelliteToMap("" + i, satellite2);

        final XdSatellite satellite3 = new XdSatellite();
        satellite3.setName("Satellite " + i);
        planet.setSatellite(satellite3);

        final XdSatellite satellite4 = new XdSatellite();
        satellite4.setName("ArrSatellite " + i);
        planet.setArrSatellites(new XdSatellite[]{satellite4});
    }

    private static XdUniverse generateBigUniverse(int countGalaxies, int countStarSystems, int countPlanets) {
        return generateBigUniverse(countGalaxies, countStarSystems, countPlanets, false);
    }

    private static XdUniverse generateBigUniverse(int countGalaxies, int countStarSystems, int countPlanets, boolean forException) {
        final XdUniverse universe = new XdUniverse();
        universe.setId(nextStringId());
        universe.setGalaxies(new ArrayList<XdGalaxy>());

        for (int i = 0; i < countGalaxies; ++i) {
            final XdGalaxy galaxy = new XdGalaxy();
            galaxy.setSystems(new LinkedList<XdStarSystem>());

            if (forException) {
                galaxy.setObject(generateObjects(1).iterator().next());
            }

            universe.addGalaxy(galaxy);

            final XdBlackHole hole = new XdBlackHole();
            hole.setId(nextStringId());

            galaxy.setHole(hole);

            for (int j = 0; j < countStarSystems; ++j) {
                final XdStarSystem system = new XdStarSystem();
                system.setId(nextStringId());
                system.setNewName("StarSystem_" + j);
                system.setStars(new LinkedList<XdStar>());
                system.setPlanets(new HashSet<XdPlanet>());

                galaxy.addSystem(system);

                XdStar star = new XdStar();
                star.setParent(system);
                star.setId(nextStringId());
                star.setName("Star_" + j);

                system.addStar(star);

                star = new XdStar();
                star.setParent(system);
                star.setId(nextStringId());
                star.setName("Star_" + (j % 3));

                system.addStar(star);

                for (int k = 0; k < countPlanets; ++k) {
                    final XdPlanet planet = generatePlanet();
//                    planet.setId(nextStringId());
                    planet.setName("Planet_" + k);
                    planet.setWaterPercent(k % 3);

                    system.addPlanet(planet);
                }
            }
        }

        return universe;
    }

    private static Collection<XdObject> generateObjects(int count) {
        final Collection<XdObject> result = new ArrayList<XdObject>(count);

        for (int i = 0; i < count; ++i) {
            final XdObject object = new XdObject();
            object.setName("Object_" + i);
            result.add(object);
        }
        return result;
    }

    private static String nextStringId() {
        return UUID.randomUUID().toString();
    }

    private static Runnable[] initOperations(final IXdStorage storage) {
        final ArrayList<Runnable> operations = new ArrayList<Runnable>();

        operations.add(new InsertUniverseOperation(storage));
        operations.add(new UpdateUniverseOperation(storage));
        operations.add(new DeleteUniverseOperation(storage));

        operations.add(new InsertGalaxyOperation(storage));
        operations.add(new UpdateGalaxyOperation(storage));
        operations.add(new DeleteGalaxyOperation(storage));

        operations.add(new InsertSystemOperation(storage));
        operations.add(new UpdateSystemOperation(storage));
        operations.add(new DeleteSystemOperation(storage));

//		operations.add(new InsertPlanetOperation(storage));
//		operations.add(new UpdatePlanetOperation(storage));
//		operations.add(new DeletePlanetOperation(storage));

//		operations.add(new insert(storage));
//		operations.add(new update(storage));
//		operations.add(new delete(storage));

        return operations.toArray(new Runnable[operations.size()]);
    }

    private static IXdStorage storage = null;

    @BeforeClass
    public static void initStorage() {
        storage = XdStorageProvider.newOrGetPGStorage("pgtest");
    }

    @AfterClass
    public static void destroyStorage() {
        deleteAllUniverses(storage);
        storage.shutdown();
    }

    @Test(expected = XdStorageException.class)
    public void testOneThreadWithException() throws XdStorageException, XdStorageConnectionException {
        for (int i = 0; i < 5; ++i) {
            XdUniverse universe = generateBigUniverse(5, 5, 10, true);

            IXdStorageTransaction tx = storage.beginTransaction();
            try {
                storage.save(universe);
                storage.save(universe.getGalaxies());
                for (final XdGalaxy galaxy : universe.getGalaxies()) {
                    storage.save(galaxy.getSystems());

                    for (final XdStarSystem system : galaxy.getSystems()) {
                        if (system != null) {
                            storage.save(system.getPlanets());
                        }
                    }
                    storage.save(galaxy.getObject());
                }

                tx.commit();
            } catch (final Throwable e) {
                log.info("error", e);
                tx.rollback();
                throw e;
            }
        }
    }

    @Test
    public void testOneThread() {
        Throwable ex = null;

        for (int i = 0; i < 5; ++i) {
            XdUniverse universe = generateBigUniverse(5, 5, 5);

            IXdStorageTransaction tx = storage.beginTransaction();
            try {
                storage.save(universe);
                storage.save(universe.getGalaxies());
                for (final XdGalaxy galaxy : universe.getGalaxies()) {
                    storage.save(galaxy.getSystems());

                    for (final XdStarSystem system : galaxy.getSystems()) {
                        if (system != null) {
                            storage.save(system.getPlanets());
                        }
                    }
                }

                tx.commit();
            } catch (final Throwable e) {
                log.info("error", e);
                ex = e;
                tx.rollback();
            }
        }

        Assert.assertNull(ex);

        final IXdStorageTransaction tx_ = storage.beginTransaction();
        try {
            Collection<XdUniverse> universes = storage.load(XdUniverse.class);
            universes.parallelStream().forEach( universe -> {
                try {
                    storage.load(universe, tx_);

                    Collection<XdGalaxy> galaxies = universe.getGalaxies();
                    storage.load(galaxies, tx_);

                    for (XdGalaxy galaxy : galaxies) {
                        Collection<XdStarSystem> systems = galaxy.getSystems();
                        for (XdStarSystem system : systems) {
                            storage.load(system, tx_);

                            if (system.getPlanets() != null) {
                                storage.delete(system.getPlanets(), tx_);
                            }

                            storage.delete(system, tx_);
                        }

                        storage.delete(galaxy, tx_);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            storage.delete(universes);

            tx_.commit();
        } catch (Throwable e) {
            log.info("error", e);
            ex = e;
            tx_.rollback();
        }

        Assert.assertNull(ex);
    }

    @Test
    public void testMultithreads() {
        final Runnable[] operations = initOperations(storage);

        Throwable ex = null;

        for (int i = 0; i < 5; ++i) {
            XdUniverse universe = generateBigUniverse(5, 5, 5);

            IXdStorageTransaction tx = storage.beginTransaction();
            try {
                storage.save(universe);
                storage.save(universe.getGalaxies());
                for (final XdGalaxy galaxy : universe.getGalaxies()) {
                    storage.save(galaxy.getSystems());

                    for (final XdStarSystem system : galaxy.getSystems()) {
                        if (system != null) {
                            storage.save(system.getPlanets());
                        }
                    }
                }

                tx.commit();
            } catch (final Throwable e) {
                log.info("error", e);
                ex = e;
                tx.rollback();
            }
        }

        Assert.assertNull(ex);

        final Throwable[] exThread = new Throwable[]{null};
        final AtomicInteger countThreads = new AtomicInteger(20);

        final Random rand = new Random(System.currentTimeMillis());
        final int count = countThreads.intValue();
        for (int i = 0; i < count; ++i) {
            final int opindex = rand.nextInt() % operations.length;
            new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        operations[Math.abs(opindex)].run();
                    } catch (final Throwable e) {
                        log.info("error", e);
                        exThread[0] = e;
                    }
                    synchronized (countThreads) {
                        countThreads.decrementAndGet();
                        countThreads.notify();
                    }
                }
            }).start();

            if (i % 5 == 0) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }

        synchronized (countThreads) {
            while (countThreads.intValue() > 0) {
                try {
                    countThreads.wait(100);
                    Assert.assertNull(exThread[0]);
                    log.info("PGStorage multithreads test: active threads = " + countThreads.get());
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }

        final IXdStorageTransaction tx_ = storage.beginTransaction();
        try {
            Collection<XdUniverse> universes = storage.load(XdUniverse.class);
            universes.parallelStream().forEach( universe -> {
                try {
                    storage.load(universe, tx_);

                    Collection<XdGalaxy> galaxies = universe.getGalaxies();
                    storage.load(galaxies, tx_);

                    for (XdGalaxy galaxy : galaxies) {
                        Collection<XdStarSystem> systems = galaxy.getSystems();
                        for (XdStarSystem system : systems) {
                            storage.load(system, tx_);

                            if (system.getPlanets() != null) {
                                storage.delete(system.getPlanets(), tx_);
                            }

                            storage.delete(system, tx_);
                        }

                        storage.delete(galaxy, tx_);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            storage.delete(universes);

            tx_.commit();
        } catch (Throwable e) {
            log.info("error", e);
            ex = e;
            tx_.rollback();
        }

        Assert.assertNull(ex);
    }
}
