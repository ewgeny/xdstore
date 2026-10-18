package org.flib.xdstorage.resource.cache;

/**
 * Тип последней мутации записи кэша ресурсов. Вынесен из XdStorageResourceCache
 * при разделении класса (ранее — приватный enum Change).
 */
public enum XdStorageCacheChange {
    undefined, read, insert, update, delete
}
