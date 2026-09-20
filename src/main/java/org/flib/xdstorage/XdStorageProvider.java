package org.flib.xdstorage;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageProvider {

    private static Map<String, XdStorage> storages = new ConcurrentHashMap<>();

    private static Lock lock = new ReentrantLock();

    public static IXdSqlStorage newOrGetPGStorage(final String name) {
        XdStorage storage = storages.get(name);
        if (storage == null) {
            lock.lock();
            try {
                storage = storages.get(name);
                if (storage == null) {
                    storages.put(name, storage = new XdStorage(name));
                }
            } finally {
                lock.unlock();
            }
        }
        return storage;
    }

    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder, final int fragmentSize) {
        XdStorage storage = storages.get(name);
        if (storage == null) {
            lock.lock();
            try {
                storage = storages.get(name);
                if (storage == null) {
                    storages.put(name, storage = new XdStorage(name, folder, fragmentSize));
                }
            } finally {
                lock.unlock();
            }
        }
        return storage;
    }

    static void unregisterStorage(final IXdStorage storage) {
        final String name = storage.getName();
        if (storages.containsKey(name)) {
            storages.remove(name);
        }
    }
}
