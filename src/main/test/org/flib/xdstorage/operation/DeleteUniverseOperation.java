package org.flib.xdstorage.operation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.entities.XdGalaxy;
import org.flib.xdstorage.entities.XdStarSystem;
import org.flib.xdstorage.entities.XdUniverse;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

public class DeleteUniverseOperation implements Runnable {

    private static final Logger     log = LogManager.getLogger(DeleteUniverseOperation.class);

    private final        IXdStorage storage;

    public DeleteUniverseOperation(final IXdStorage storage) {
        this.storage = storage;
    }

    final Random rand = new Random();

    @Override
    public void run() {
        System.out.println("DeleteUniverseOperation Started");
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final Collection<XdUniverse> universes = storage.load(XdUniverse.class);
            final int universeIndex = Math.abs(rand.nextInt()) % (universes.size() == 0 ? 1 : universes.size());
            Iterator<XdUniverse> it = universes.iterator();
            for (int i = 0; it.hasNext(); ++i) {
                final XdUniverse universe = it.next();
                if (i == universeIndex) {

                    final Collection<XdGalaxy> galaxies = universe.getGalaxies();
                    storage.load(galaxies);

                    for (final XdGalaxy galaxy : galaxies) {
                        Collection<XdStarSystem> systems = galaxy.getSystems();
                        if (systems != null && systems.size() > 0) {
                            storage.load(systems);

                            for (XdStarSystem system : systems) {
                                storage.delete(system.getPlanets());
                            }
                            storage.delete(systems);
                        }
//                    if (galaxy.getObject() != null) {
//                        storage.delete(galaxy.getObject());
//                    }
                    }
                    storage.delete(galaxies);
                    storage.delete(universe);

                    break;
                }
            }

            tx.commit();
            System.out.println("DeleteUniverseOperation Commited");
        } catch (final XdStorageException | XdStorageConnectionException e) {
            log.info("error", e);
            tx.rollback();
            System.out.println("DeleteUniverseOperation Rolledback");
        }
    }

}
