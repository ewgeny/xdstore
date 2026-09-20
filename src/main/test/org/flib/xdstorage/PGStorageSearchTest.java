package org.flib.xdstorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.entities.*;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.search.query.*;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.*;

@FixMethodOrder(MethodSorters.JVM)
public class PGStorageSearchTest {

    private static final Logger log = LogManager.getLogger(PGStorageSearchTest.class);

    private static IXdSqlStorage storage = null;

    @BeforeClass
    public static void initStorage() {
        storage = XdStorageProvider.newOrGetPGStorage("pgtest");

        for (int i = 0; i < 5; ++i) {
            XdUniverse universe = generateBigUniverse(5, 5, 10);

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
                tx.rollback();
            }
        }
    }

    @AfterClass
    public static void destroyStorage() {
//        deleteAllUniverses(storage);
        storage.shutdown();
    }

    @Test
    public void test1() {
        IXdStorageTransaction tx = storage.beginTransaction();
        try {

            IXdStorageSqlCriterion criterionWaterPercent2 =
                    new XdStorageSqlCriterion(XdConstants.WATER_PERCENT_FIELD, XdStorageSqlCriterionOperator.EQUALS, new Integer(2));
            IXdStorageSqlPrimaryCriterion criterionPlanet8 =
                    new XdStorageSqlPrimaryCriterion(XdStorageSqlCriterionOperator.EQUALS, "Planet8");
            IXdStorageSqlCriterion criterionWaterPercent1 =
                    new XdStorageSqlCriterion(XdConstants.WATER_PERCENT_FIELD, XdStorageSqlCriterionOperator.EQUALS, new Integer(1));
            IXdStorageSqlPrimaryCriterion criterionPlanet4 =
                    new XdStorageSqlPrimaryCriterion(XdStorageSqlCriterionOperator.EQUALS, "Planet4");

            XdStorageSqlSearchQuery query = new XdStorageSqlSearchQuery();
            query.or(criterionPlanet8, criterionWaterPercent2);
            query.or(criterionPlanet4, criterionWaterPercent1);

            Collection<XdPlanet> planets = storage.load(XdPlanet.class, XdConstants.TEST_INDEX_NAME, query);
            for (XdPlanet planet : planets) {
                log.info(planet.toString());
            }

            tx.commit();
        } catch (XdStorageException | XdStorageConnectionException ex) {
            log.info("error", ex);
            tx.rollback();
        }
    }

    @Test
    public void test2() {
        IXdStorageTransaction tx = storage.beginTransaction();
        try {

            IXdStorageSqlCriterion criterion1 =
                    new XdStorageSqlChildCriterion("stars", XdStar.class, XdConstants.NAME_FIELD, XdStorageSqlCriterionOperator.EQUALS, "Star_4");

            IXdStorageSqlCriterion criterion2 =
                    new XdStorageSqlChildCriterion("stars", XdStar.class, XdConstants.NAME_FIELD, XdStorageSqlCriterionOperator.LIKE, "%8");
            IXdStorageSqlCriterion criterion3 =
                    new XdStorageSqlChildCriterion("stars", XdStar.class, XdConstants.NAME_FIELD, XdStorageSqlCriterionOperator.EQUALS, "Star_2");

            IXdStorageSqlCriterion complex = new XdStorageSqlComplexCriterion(criterion2).and(criterion3);

            XdStorageSqlSearchQuery query = new XdStorageSqlSearchQuery();
            query.or(null, criterion1);
            query.or(null, complex);

            Collection<XdStarSystem> systems = storage.load(XdStarSystem.class, XdConstants.TEST_STAR_INDEX_NAME, query);
            for (XdStarSystem system : systems) {
                log.info(system.toString());
            }

            tx.commit();
        } catch (XdStorageException | XdStorageConnectionException ex) {
            log.info("error", ex);
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
                system.setPlanets(new HashSet<>());

                galaxy.addSystem(system);

                if (i % 5 == 0) {
                    XdStar star = new XdStar();
                    star.setId(nextStringId());
                    star.setName("Star_8");

                    system.addStar(star);

                    star = new XdStar();
                    star.setId(nextStringId());
                    star.setName("Star_2");

                    system.addStar(star);
                } else {
                    XdStar star = new XdStar();
                    star.setId(nextStringId());
                    star.setName("Star_" + j);

                    system.addStar(star);
                }

                for (int k = 0; k < countPlanets; ++k) {
                    final XdPlanet planet = new XdPlanet();
//                    planet.setId(nextStringId());
                    planet.setName("Planet" + k);
                    planet.setWaterPercent(k % 3);

                    system.addPlanet(planet);
                }
            }
        }

        return universe;
    }

    private static void deleteAllUniverses(IXdStorage storage) {
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
            log.info("CLEARING STORE SUCCESS");
        } catch (XdStorageException | XdStorageConnectionException e) {
            tx_.rollback();
            log.info("CLEARING STORE FAILED");
            log.info("error", e);
        }
    }

    private static String nextStringId() {
        return UUID.randomUUID().toString();
    }
}
