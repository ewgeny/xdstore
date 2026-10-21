package io.github.xdstorage.resource;

/** Уровень изоляции чтений кэша ресурсов. */
public enum XdStorageIsolationLevel {
    /** Повторяемое чтение/снимок: читатель, начавший до чужого коммита, видит старую версию. */
    SNAPSHOT,
    /** Read Committed: читатель всегда видит ПОСЛЕДНЕЕ закоммиченное состояние. */
    READ_COMMITTED
}
