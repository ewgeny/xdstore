package org.flib.xdstorage.operation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.entities.XdGalaxy;
import org.flib.xdstorage.entities.XdUniverse;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

public class UpdateGalaxyOperation implements Runnable {

    private static final Logger     log = LogManager.getLogger(UpdateGalaxyOperation.class);

    private final        IXdStorage storage;

    public UpdateGalaxyOperation(final IXdStorage storage) {
        this.storage = storage;
    }

    final Random rand = new Random();

    @Override
    public void run() {
        System.out.println("UpdateGalaxyOperation Started");
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
                            final int index = Math.abs(rand.nextInt()) % universe.getGalaxies().size();
                            final XdGalaxy galaxy = universe.getGalaxy(index);

                            storage.load(galaxy);
                            storage.update(galaxy);
//                            if (galaxy.getObject() != null)
//                                storage.update(galaxy.getObject());
                        }
                        break;
                    }
                }
            }

            tx.commit();
            System.out.println("UpdateGalaxyOperation Commited");
        } catch (final XdStorageException | XdStorageConnectionException e) {
            log.info("error", e);
            tx.rollback();
            System.out.println("UpdateGalaxyOperation Rolledback");
        }
    }

}
