package org.flib.xdstorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.flib.xdstorage.serialization.FilesFormat;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageProvider {

    private static final Logger log = LogManager.getLogger(XdStorageProvider.class);

    private static Map<String, XdStorage> storages = new ConcurrentHashMap<>();

    private static Lock lock = new ReentrantLock();

    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder, final int fragmentSize) {
        return newOrGetFileStorage(name, folder, fragmentSize, FilesFormat.YAML);
    }

    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder, final int fragmentSize, FilesFormat format) {
        XdStorage storage = storages.get(name);
        if (storage == null) {
            lock.lock();
            try {
                storage = storages.get(name);
                if (storage == null) {
                    log.info("storage '{}' opened at {} ({})", name, folder, format);
                    storages.put(name, storage = new XdStorage(name, folder, fragmentSize, format));
                    return storage;
                }
            } finally {
                lock.unlock();
            }
        }
        // GOTCHA для тестов и встраивания: реестр держит инстанс по ИМЕНИ до shutdown() —
        // повторный вызов с тем же именем вернет СТАРЫЙ инстанс (с его кэшами и файлами),
        // даже если каталог удалили и пересоздали. Без этого WARN такие сюрпризы не видны.
        log.warn("storage '{}' requested again — returning the EXISTING instance (registry holds it until shutdown())", name);
        return storage;
    }

    static void unregisterStorage(final IXdStorage storage) {
        final String name = storage.getName();
        if (storages.containsKey(name)) {
            log.info("storage '{}' shut down and unregistered", name);
            storages.remove(name);
        }
    }


}
