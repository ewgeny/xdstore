package io.github.xdstorage.transaction;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.exceptions.XdStorageException;

public interface IXdStorageTransaction {

    IXdStorageTransactionManager<?> getTransactionManager();

    long getTimestart();

    IXdStorage getStorage();

    String getTransactionThreadId();

    String getTransactionId();

    long getTimeout();

    void commit();

    void rollback();

    void markRollbackOnly();

    boolean isRollbackOnly();

    /**
     * R-57: регистрация успешно вставленного ключа индексного Б+Дерева для
     * КОМПЕНСИРУЮЩЕГО удаления при откате транзакции. Мутации дерева выполняются
     * ин-плейс в живом каноническом графе и НЕОБРАТИМЫ откатом record-состояний:
     * без компенсации ключ индекса переживает откат своей транзакции, навсегда
     * указывая читателям сканов на несуществующую запись ("cannot load by reference").
     */
    void registerIndexKeyForRollback(Object tree, Comparable key);

    /**
     * R-57: компенсирующее удаление всех зарегистрированных индексных ключей
     * (обратный порядок регистрации). Ошибки удаления ключей-отсутствий глушатся
     * с WARN: ключ мог не дожить до компенсации по той же причине, что и откат.
     */
    void rollbackIndexKeyInserts();
}
