package io.github.xdstorage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.xdstorage.serialization.FilesFormat;
import io.github.xdstorage.utils.XdStorageAtomicFile;
import io.github.xdstorage.utils.XdStorageFolderLock;
import io.github.xdstorage.utils.XdStorageFormatMeta;
import io.github.xdstorage.utils.XdStorageVersion;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageProvider {

    private static final Logger log = LogManager.getLogger(XdStorageProvider.class);

    /**
     * РАЗМЕР ФРАГМЕНТА ПО УМОЛЧАНИЮ (объектов на файл класса, политика StoreAsClassObjects).
     * 128 — измеренный оптимум конфигурационного свипа (docs/PERFORMANCE-RECOMMENDATIONS.md §5):
     * минимум латентности коммита, лучшая скорость вставки, ровные сканы; ~десятки-сотни КБ на файл
     * для типичных небольших сущностей. Файлы фрагментов > ~1 МБ — красный флаг (перезапись целого
     * файла на каждый коммит), < ~10 КБ — накладные расходы на файл (холодный скан, открытые fd).
     * Диапазон 128-512 оставляет запас для сущностей крупнее эталонных ~430 байт.
     */
    public static final int DEFAULT_FRAGMENT_SIZE = 128;

    /**
     * A2: the library version (semver, e.g. "2.0.0") — the same value a storage instance
     * returns from {@code getVersion()}. Read from the Maven-filtered
     * {@code /xdstorage.properties} resource; "unknown" without Maven filtering.
     */
    public static String getVersion() {
        return XdStorageVersion.getVersion();
    }

    private static Map<String, XdStorage> storages = new ConcurrentHashMap<>();

    private static Lock lock = new ReentrantLock();

    /** Открытие с рекомендованным размером фрагмента {@link #DEFAULT_FRAGMENT_SIZE} и конфигурацией по умолчанию (YAML). */
    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder) {
        return newOrGetFileStorage(name, folder, DEFAULT_FRAGMENT_SIZE, FilesFormat.YAML, XdStorageConfig.defaults());
    }

    /** Открытие с рекомендованным размером фрагмента {@link #DEFAULT_FRAGMENT_SIZE}, явной конфигурацией, формат YAML. */
    public static IXdFileStorage newOrGetFileStorage(final String name, final String folder, final XdStorageConfig config) {
        return newOrGetFileStorage(name, folder, DEFAULT_FRAGMENT_SIZE, FilesFormat.YAML, config);
    }

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
                        throw new io.github.xdstorage.exceptions.XdStorageRuntimeException(
                                "storage folder " + folder + " cannot be locked for exclusive use", e);
                    }
                    boolean created = false;
                    try {
                        // A2: format marker under the exclusive folder lock, before any engine state
                        // is built — announces "opened storage X, format vN", initializes the marker
                        // on a fresh folder, flags a legacy 1.x folder, rejects a newer-format folder.
                        XdStorageFormatMeta.onOpen(new File(folder), name, format.name());
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
