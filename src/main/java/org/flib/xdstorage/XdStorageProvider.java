package org.flib.xdstorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.flib.xdstorage.serialization.FilesFormat;
import org.flib.xdstorage.utils.XdStorageAtomicFile;
import org.flib.xdstorage.utils.XdStorageFolderLock;

import java.io.File;
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
        return newOrGetFileStorage(name, folder, fragmentSize, FilesFormat.YAML, XdStorageConfig.defaults());
    }

    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder, final int fragmentSize, FilesFormat format) {
        return newOrGetFileStorage(name, folder, fragmentSize, format, XdStorageConfig.defaults());
    }

    /** Config overload: YAML format (the engine default) with explicit open-time configuration. */
    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder, final int fragmentSize, final XdStorageConfig config) {
        return newOrGetFileStorage(name, folder, fragmentSize, FilesFormat.YAML, config);
    }

    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder, final int fragmentSize,
                                                     final FilesFormat format, final XdStorageConfig config) {
        final XdStorageConfig cfg = config == null ? XdStorageConfig.defaults() : config;
        XdStorage storage = storages.get(name);
        if (storage == null) {
            lock.lock();
            try {
                storage = storages.get(name);
                if (storage == null) {
                    // S-3: эксклюзивная блокировка каталога (между процессами и инстансами JVM).
                    // Checked-исключение в сигнатуру добавить нельзя (ломает API): занятый каталог
                    // = unchecked XdStorageFolderLock.XdStorageInUseException, IOError = unchecked обертка
                    final XdStorageFolderLock folderLock;
                    try {
                        folderLock = XdStorageFolderLock.acquire(new File(folder));
                    } catch (final java.io.IOException e) {
                        throw new org.flib.xdstorage.exceptions.XdStorageRuntimeException(
                                "storage folder " + folder + " cannot be locked for exclusive use", e);
                    }
                    boolean created = false;
                    try {
                        log.info("storage '{}' opened at {} ({})", name, folder, format);
                        storage = new XdStorage(name, folder, fragmentSize, format, cfg);
                        storage.attachFolderLock(folderLock);
                        created = true;
                    } finally {
                        if (!created)
                            folderLock.close();
                    }
                    storages.put(name, storage);

                    // S-5: зачистка .tmp-мусора прошлого сбоя + сводка открытия (объектные
                    // счетчики неизвестны до ленивой prepare — считаем файлы)
                    final File root = new File(folder);
                    final int swept = XdStorageAtomicFile.sweepStaleTempFiles(root);
                    log.info("storage '{}' ready: {} structure file(s), {} data file(s), {} stale .tmp swept; config {}",
                            name, countFiles(new File(root, "structure"), false),
                            countFiles(new File(root, "data"), true), swept, cfg);
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

    private static int countFiles(final File dir, final boolean recursive) {
        if (dir == null || !dir.isDirectory())
            return 0;
        final File[] entries = dir.listFiles();
        if (entries == null)
            return 0;
        int count = 0;
        for (final File entry : entries) {
            if (entry.isDirectory()) {
                if (recursive)
                    count += countFiles(entry, true);
            } else if (!entry.getName().endsWith(".tmp")) {
                count++;
            }
        }
        return count;
    }

    static void unregisterStorage(final IXdStorage storage) {
        final String name = storage.getName();
        if (storages.containsKey(name)) {
            log.info("storage '{}' shut down and unregistered", name);
            storages.remove(name);
        }
    }


}
