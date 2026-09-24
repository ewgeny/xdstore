package org.flib.xdstorage.operation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.entities.XdUniverse;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.util.Collection;
import java.util.Iterator;
import java.util.Random;

public class UpdateUniverseOperation implements Runnable {

    private static final Logger     log = LogManager.getLogger(UpdateUniverseOperation.class);

    private final        IXdStorage storage;

    public UpdateUniverseOperation(final IXdStorage storage) {
        this.storage = storage;
    }

    final Random rand = new Random();

    @Override
    public void run() {
        System.out.println("UpdateUniverseOperation Started");
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            final Collection<XdUniverse> universes = storage.load(XdUniverse.class);
            final int universeIndex = Math.abs(rand.nextInt()) % (universes.size() == 0 ? 1 : universes.size());
            Iterator<XdUniverse> it = universes.iterator();
            for (int i = 0; it.hasNext(); ++i) {
                final XdUniverse universe = it.next();
                if (i == universeIndex) {
                    storage.update(universe);
                    break;
                }
            }

            tx.commit();
            System.out.println("UpdateUniverseOperation Commited");
        } catch (final XdStorageException | XdStorageConnectionException e) {
            log.info("error", e);
            tx.rollback();
            System.out.println("UpdateUniverseOperation Rolledback");
        }
    }

}
