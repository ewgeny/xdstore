package org.flib.xdstorage.operation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XmlDataStorageMultithreadsTest;
import org.flib.xdstorage.entities.XdGalaxy;
import org.flib.xdstorage.entities.XdStarSystem;
import org.flib.xdstorage.entities.XdUniverse;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

public class UpdateSystemOperation implements Runnable {

    private static final Logger     log = LogManager.getLogger(UpdateSystemOperation.class);
    private final        IXdStorage storage;

    public UpdateSystemOperation(final IXdStorage storage) {
        this.storage = storage;
    }

    final Random rand = new Random();

    @Override
    public void run() {
        System.out.println("UpdateSystemOperation Started");
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final Collection<XdUniverse> universes = storage.load(XdUniverse.class);
            if (universes.size() > 0) {
                final int universeIndex = Math.abs(rand.nextInt()) % universes.size();
                Iterator<XdUniverse> it = universes.iterator();
                for (int i = 0; it.hasNext(); ++i) {
                    final XdUniverse universe = it.next();
                    if (i == universeIndex) {
                        if (universe.getGalaxies().size() > 0) {
                            final int galaxyIndex = Math.abs(rand.nextInt()) % universe.getGalaxies().size();
                            final XdGalaxy galaxy = universe.getGalaxy(galaxyIndex);

                            storage.load(galaxy);

                            if (galaxy.getSystems() != null && galaxy.getSystems().size() > 0) {
                                final int index = Math.abs(rand.nextInt()) % galaxy.getSystems().size();
                                final XdStarSystem system = galaxy.getSystem(index);

                                storage.load(system);
                                storage.update(system);
                            }
                        }
                        break;
                    }
                }
            }

            tx.commit();
            System.out.println("UpdateSystemOperation Commited");
        } catch (final XdStorageException | XdStorageConnectionException e) {
            log.info("error", e);
            tx.rollback();
            System.out.println("UpdateSystemOperation Rolledback");
        }
    }

}
