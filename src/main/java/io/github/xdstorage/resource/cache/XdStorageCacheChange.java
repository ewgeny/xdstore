package io.github.xdstorage.resource.cache;

/**
 * Тип последней мутации записи кэша ресурсов. Вынесен из XdStorageResourceCache
 * при разделении класса (ранее — приватный enum Change).
 */
public enum XdStorageCacheChange {

    /** No pending change: the record's current marker after a commit made the version permanent. */
    undefined,

    /** Clean read: the record holds a materialized committed object with no transactional mutation. */
    read,

    /** Uncommitted insert: the object is new to this cache and its content lives in the pending slot. */
    insert,

    /** Uncommitted update: the record keeps the previous object and the modified one side by side. */
    update,

    /** Uncommitted delete: the object is marked for removal once the transaction commits. */
    delete
}
