package org.flib.xdstorage.operation;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.entities.XdObject;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

public class InsertObject implements Runnable {

    private final IXdStorage storage;

    private long counter = 0;

    public InsertObject(final IXdStorage storage) {
        this.storage = storage;
    }

    @Override
    public void run() {
        System.out.println("InsertObject Started");
        final IXdStorageTransaction tx = storage.beginTransaction();
        try {
            storage.save(instanceNewObject());

            tx.commit();
            System.out.println("InsertObject Commited");
        } catch (final XdStorageException | XdStorageConnectionException e) {
            tx.rollback();
            System.out.println("InsertObject Rolledback");
        }
    }

    private XdObject instanceNewObject() {
        XdObject result = new XdObject();
//        result.setObjectId(generateId());
        return result;
    }

    private synchronized Long generateId() {
        return counter++;
    }
}
