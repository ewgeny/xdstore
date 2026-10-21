package io.github.xdstorage.resource.cache;

/**
 * Материализованный (прочитанный/транзитный) объект в контуре транзакции:
 * результат клонирования при чтении или собственная мутация, ожидающая коммита.
 * Вынесен из XdStorageResourceCache при разделении класса (ранее — приватный
 * внутренний класс ObjectChange).
 */
public final class XdStorageCacheObjectChange {

    public Object object;

    public final XdStorageCacheChange change;

    public final long readTimestamp;

    /** Версия записи кэша на момент материализации (для RC-освежения). */
    public volatile long recordVersion = -1;

    public XdStorageCacheObjectChange(final Object object, final XdStorageCacheChange change, final long readTimestamp) {
        this.object = object;
        this.change = change;
        this.readTimestamp = readTimestamp;
    }
}
