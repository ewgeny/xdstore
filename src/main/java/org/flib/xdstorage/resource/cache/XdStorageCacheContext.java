package org.flib.xdstorage.resource.cache;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.XdStorageIsolationLevel;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Map;

/**
 * Внутренний SPI движка кэша: то, что коллабораторам (запись, мутатор, протокол
 * коммита) нужно от фасада {@link org.flib.xdstorage.resource.XdStorageResourceCache}.
 * Выделен интерфейсом при переносе коллабораторов в подпакет cache — это делает
 * зависимость явной, а инварианты классов проверяемыми юнит-тестами без фасада.
 */
public interface XdStorageCacheContext {

    /** Поле идентификатора сущности данного ресурса. */
    XdStorageObjectIdField idField();

    /** Реестр записей кэша: id объекта -> MVCC-запись. */
    Map<Object, XdStorageCacheRecord> records();

    /** Транзакционные контуры (write-set / журнал отката / неидентифицированные / прочитанное). */
    XdStorageTransactionWorkspace workspace();

    /** Кэш обслуживает инфраструктуру B+Дерева (деревья/узлы) — живые канонические инстансы. */
    boolean isInfrastructureCache();

    /** Кэш обслуживает именно НОДЫ дерева — полная структурная релаксация (R-42). */
    boolean isStructuralNodeCache();

    /** Класс сущности данного ресурса (для сообщений об отказах). */
    Class<?> entityClass();

    /** Жива ли транзакция (контур мертвых владельцев записей). */
    boolean isTransactionAlive(XdStorageTransaction transaction);

    /** Уровень изоляции ресурса (SNAPSHOT | READ_COMMITTED). */
    XdStorageIsolationLevel getIsolationLevel();

    /** Запись чистого чтения (объект пришел с диска / из живой структуры). */
    XdStorageCacheRecord createReadRecord(Object object);

    /** Запись незакоммиченной вставки (контент в newObject). */
    XdStorageCacheRecord createInsertRecord(Object newObject, XdStorageTransaction transaction);

    /**
     * Материализация объекта для записи в кэш: живая инстанция для B+ Дерева,
     * изолированный клон для доменных объектов (+ канонизация в реестре).
     */
    Object materializeForCache(Object newObject) throws XdStorageException;

    /**
     * Полная публичная вставка фасада (защита от дубликатов + критическая секция):
     * нужна мутатору для самозарвки инфраструктурных объектов (R-40).
     */
    void insert(Object newObject, XdStorageTransaction transaction) throws XdStorageException;
}
